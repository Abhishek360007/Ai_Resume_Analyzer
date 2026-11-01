package com.abhishek.resumeanalyzer.controller;

import com.abhishek.resumeanalyzer.entities.ProcessTracker;
import com.abhishek.resumeanalyzer.models.TrackerStatusEvent;
import com.abhishek.resumeanalyzer.services.FileUploadService;
import com.abhishek.resumeanalyzer.services.TrackerEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for file upload operations.
 * GraphQL does not support file uploads well, so we use REST for this endpoint.
 */
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class FileUploadController {

    private final FileUploadService fileUploadService;
    private final TrackerEventPublisher trackerEventPublisher;

    /**
     * Handle resume file upload (single file or ZIP).
     * Returns a tracking UUID for status monitoring.
     * 
     * @param file Uploaded file
     * @return Response with tracker UUID
     */
    @PostMapping("/resume")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public ResponseEntity<Map<String, Object>> uploadResume(
            @RequestParam("files") java.util.List<MultipartFile> files) {
        
        log.info("Received upload request with {} files", files.size());

        try {
            UUID trackerId = fileUploadService.handleMultipleFileUpload(files);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("trackerId", trackerId.toString());
            response.put("message", "File upload initiated successfully");
            response.put("filename", files.size() + " files");

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Validation error: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);

        } catch (Exception e) {
            log.error("Upload error", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Internal server error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Get processing status for a tracker ID.
     * 
     * @param trackerId Tracker UUID
     * @return Process tracker details
     */
    @GetMapping("/status/{trackerId}")
    public ResponseEntity<ProcessTracker> getUploadStatus(@PathVariable String trackerId) {
        try {
            UUID uuid = UUID.fromString(trackerId);
            ProcessTracker tracker = fileUploadService.getProcessStatus(uuid);
            return ResponseEntity.ok(tracker);
        } catch (IllegalArgumentException e) {
            log.error("Invalid tracker ID: {}", trackerId);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Server-Sent Events stream of live processing status.
     *
     * <p>On connect, the current state of every active (non-terminal) tracker is
     * replayed as {@code SNAPSHOT} events, then every subsequent background update
     * is pushed to the client as it happens.  The browser connects with
     * {@code EventSource("/api/upload/status/events?token=...")} because the
     * {@code EventSource} API cannot set Authorization headers.</p>
     *
     * @return an infinite SSE stream (heartbeat comment every 15s)
     */
    @GetMapping(value = "/status/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamStatusEvents() {
        SseEmitter emitter = trackerEventPublisher.subscribe();
        log.info("SSE client connected: activeEmitters={}", trackerEventPublisher.getEmitterCount());

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Accel-Buffering", "no");
        headers.setCacheControl("no-cache");

        // Replay current state so a reconnecting / refreshed client never misses progress
        try {
            List<ProcessTracker> active = fileUploadService.getActiveTrackers();
            for (ProcessTracker tracker : active) {
                emitter.send(SseEmitter.event().name(TrackerStatusEvent.TYPE_SNAPSHOT)
                        .data(TrackerStatusEvent.from(tracker, TrackerStatusEvent.TYPE_SNAPSHOT)));
            }
        } catch (IOException | IllegalStateException e) {
            log.warn("Failed to send tracker snapshot: {}", e.getMessage());
        }

        return ResponseEntity.ok().headers(headers).body(emitter);
    }
}
