package com.abhishek.resumeanalyzer.services;

import com.abhishek.resumeanalyzer.entities.ProcessTracker;
import com.abhishek.resumeanalyzer.models.JobPriority;
import com.abhishek.resumeanalyzer.models.JobType;
import com.abhishek.resumeanalyzer.models.ProcessStatus;
import com.abhishek.resumeanalyzer.models.TrackerStatusEvent;
import com.abhishek.resumeanalyzer.repos.ProcessTrackerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Service for handling file uploads.
 * Validates files and enqueues resume processing jobs into the job queue.
 * The Apache Pekko pipeline (see com.abhishek.resumeanalyzer.pekko) consumes the jobs
 * and runs the parsing / AI analysis / embedding pipeline.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileUploadService {

    private final FileParserService fileParserService;
    private final ProcessTrackerRepository trackerRepository;
    private final JobQueueService jobQueueService;
    private final TrackerEventPublisher trackerEventPublisher;

    @Value("${app.upload.directory:./uploads}")
    private String uploadDirectory;

    @Value("${app.upload.allowed-extensions:.doc,.docx,.pdf}")
    private String allowedExtensions;

    /**
     * Handle multiple file upload.
     * Creates a process tracker and enqueues one job per file (or per ZIP entry).
     *
     * @param files List of uploaded files
     * @return UUID of the process tracker
     * @throws IOException if file operations fail
     */
    public UUID handleMultipleFileUpload(List<MultipartFile> files) throws IOException {
        log.info("Handling multiple file upload, count: {}", files.size());

        if (files.isEmpty()) {
            throw new IllegalArgumentException("No files provided");
        }

        // Validate all files first
        for (MultipartFile file : files) {
            validateFile(file);
        }

        // Create process tracker
        ProcessTracker tracker = ProcessTracker.builder()
                .status(ProcessStatus.INITIATED)
                .uploadedFilename(files.size() + " files")
                .totalFiles(files.size())
                .processedFiles(0)
                .failedFiles(0)
                .message("Received " + files.size() + " files, processing initiated")
                .build();

        tracker = trackerRepository.save(tracker);
        String correlationId = "batch-" + tracker.getId().toString();
        tracker.setCorrelationId(correlationId);
        trackerRepository.save(tracker);

        log.info("Created process tracker for batch: {}, correlationId={}", tracker.getId(), correlationId);

        // A single ZIP upload is expanded into one job per contained resume.
        boolean singleZip = files.size() == 1 && isZip(files.get(0).getOriginalFilename());

        int jobCount;
        if (singleZip) {
            MultipartFile zip = files.get(0);
            jobCount = createJobsFromZip(zip.getBytes(), zip.getOriginalFilename(), tracker.getId(), correlationId);
            tracker.setTotalFiles(jobCount);
        } else {
            jobCount = 0;
            for (MultipartFile file : files) {
                createResumeJob(file.getBytes(), file.getOriginalFilename(), tracker.getId(), correlationId);
                jobCount++;
            }
        }

        if (jobCount == 0) {
            tracker.updateStatus(ProcessStatus.FAILED, "No supported files found in the ZIP archive");
        } else {
            tracker.updateStatus(ProcessStatus.INITIATED,
                    String.format("Created %d job(s) in queue for processing", jobCount));
        }
        trackerRepository.save(tracker);
        trackerEventPublisher.publish(tracker, TrackerStatusEvent.TYPE_UPDATE);

        log.info("Created {} jobs in queue for batch upload", jobCount);
        return tracker.getId();
    }

    /**
     * Handle single or ZIP file upload.
     * Creates a process tracker and enqueues a resume processing job.
     *
     * @param file Uploaded file
     * @return UUID of the process tracker
     * @throws IOException if file operations fail
     */
    public UUID handleFileUpload(MultipartFile file) throws IOException {
        log.info("Handling file upload: {}", file.getOriginalFilename());

        // Validate file
        validateFile(file);

        // Create upload directory if it doesn't exist
        Path uploadPath = Paths.get(uploadDirectory);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Create process tracker
        ProcessTracker tracker = ProcessTracker.builder()
                .status(ProcessStatus.INITIATED)
                .uploadedFilename(file.getOriginalFilename())
                .totalFiles(1)
                .processedFiles(0)
                .failedFiles(0)
                .message("Upload received, processing initiated")
                .build();

        tracker = trackerRepository.save(tracker);
        String correlationId = "upload-" + tracker.getId().toString();
        tracker.setCorrelationId(correlationId);
        trackerRepository.save(tracker);

        log.info("Created process tracker: {}, correlationId={}", tracker.getId(), correlationId);

        byte[] fileData = file.getBytes();
        String filename = file.getOriginalFilename();

        int jobCount;
        if (isZip(filename)) {
            jobCount = createJobsFromZip(fileData, filename, tracker.getId(), correlationId);
            tracker.setTotalFiles(jobCount);
        } else {
            createResumeJob(fileData, filename, tracker.getId(), correlationId);
            jobCount = 1;
        }

        if (jobCount == 0) {
            tracker.updateStatus(ProcessStatus.FAILED, "No supported files found in the ZIP archive");
        } else {
            tracker.updateStatus(ProcessStatus.INITIATED,
                    String.format("Created %d job(s) in queue for processing", jobCount));
        }
        trackerRepository.save(tracker);
        trackerEventPublisher.publish(tracker, TrackerStatusEvent.TYPE_UPDATE);

        log.info("Created {} job(s) in queue: filename={}, trackerId={}", jobCount, filename, tracker.getId());
        return tracker.getId();
    }

    /**
     * Expand a ZIP upload into one resume processing job per supported file.
     *
     * @param zipData Binary ZIP content
     * @param zipFilename Original ZIP filename
     * @param trackerId Process tracker ID
     * @param correlationId Correlation ID for the upload batch
     * @return Number of jobs created
     * @throws IOException if the ZIP cannot be read
     */
    private int createJobsFromZip(byte[] zipData, String zipFilename, UUID trackerId, String correlationId)
            throws IOException {
        log.info("Expanding ZIP upload: {}", zipFilename);

        int jobCount = 0;
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = entry.getName();
                if (!fileParserService.isValidFileFormat(entryName)) {
                    log.warn("Skipping unsupported file in ZIP: {}", entryName);
                    continue;
                }
                byte[] entryData = zipInputStream.readAllBytes();
                createResumeJob(entryData, entryName, trackerId, correlationId);
                jobCount++;
            }
        }
        log.info("Created {} jobs from ZIP: {}", jobCount, zipFilename);
        return jobCount;
    }

    /**
     * Enqueue a single resume processing job.
     */
    private void createResumeJob(byte[] fileData, String filename, UUID trackerId, String correlationId) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("filename", filename);
        metadata.put("trackerId", trackerId.toString());
        metadata.put("uploadedAt", java.time.LocalDateTime.now().toString());
        metadata.put("fileSize", fileData.length);

        jobQueueService.createJob(
                JobType.RESUME_PROCESSING,
                fileData,
                metadata,
                JobPriority.NORMAL,
                correlationId
        );
        log.debug("Job created for file: filename={}, trackerId={}", filename, trackerId);
    }

    /**
     * Validate uploaded file.
     * Checks file size, extension, and content.
     *
     * @param file Uploaded file
     * @throws IllegalArgumentException if validation fails
     */
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Filename is required");
        }

        // Check if ZIP or allowed resume format
        boolean isZip = filename.toLowerCase().endsWith(".zip");
        if (!isZip && !fileParserService.isValidFileFormat(filename)) {
            throw new IllegalArgumentException(
                "Unsupported file format. Allowed formats: " + allowedExtensions + ", .zip"
            );
        }

        // Check file size (already handled by Spring Boot multipart config,
        // but we can add custom validation here)
        long maxSize = 50 * 1024 * 1024; // 50 MB
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException(
                "File size exceeds maximum allowed size of 50 MB"
            );
        }

        log.debug("File validation passed: {}", filename);
    }

    /**
     * Get process tracker by ID.
     *
     * @param trackerId Tracker UUID
     * @return Process tracker
     */
    public ProcessTracker getProcessStatus(UUID trackerId) {
        return trackerRepository.findById(trackerId)
                .orElseThrow(() -> new IllegalArgumentException("Tracker not found: " + trackerId));
    }

    /**
     * Get all trackers that are still being processed (not completed or failed).
     * Used to replay the current state to a newly connected SSE client.
     *
     * @return list of in-progress process trackers
     */
    public List<ProcessTracker> getActiveTrackers() {
        return trackerRepository.findByStatusNotIn(List.of(ProcessStatus.COMPLETED, ProcessStatus.FAILED));
    }

    private boolean isZip(String filename) {
        return filename != null && filename.toLowerCase().endsWith(".zip");
    }
}
