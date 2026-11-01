package com.abhishek.resumeanalyzer.models;

import com.abhishek.resumeanalyzer.entities.ProcessTracker;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Real-time processing status event pushed to connected clients over
 * Server-Sent Events.
 *
 * <p>Carries a point-in-time snapshot of a {@link ProcessTracker} plus a
 * {@code type} discriminator so clients can react to the kind of change
 * (initial batch, stage update, per-file completion, permanent failure).</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackerStatusEvent {

    /** Emitted when a client connects — the current state of all active trackers. */
    public static final String TYPE_SNAPSHOT = "SNAPSHOT";

    /** Emitted on any non-terminal tracker change (stage transitions, retry messages). */
    public static final String TYPE_UPDATE = "UPDATE";

    /** Emitted when a single file in the batch finished processing. */
    public static final String TYPE_PROCESSED = "PROCESSED";

    /** Emitted when a file (or the whole batch) failed permanently. */
    public static final String TYPE_FAILED = "FAILED";

    private String type;
    private UUID trackerId;
    private String status;
    private Integer totalFiles;
    private Integer processedFiles;
    private Integer failedFiles;
    private String message;
    private String uploadedFilename;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;

    /**
     * Build an event from the current state of a process tracker.
     *
     * @param tracker source tracker
     * @param type    event discriminator ({@link #TYPE_UPDATE}, etc.)
     */
    public static TrackerStatusEvent from(ProcessTracker tracker, String type) {
        return TrackerStatusEvent.builder()
                .type(type)
                .trackerId(tracker.getId())
                .status(tracker.getStatus() != null ? tracker.getStatus().name() : null)
                .totalFiles(tracker.getTotalFiles())
                .processedFiles(tracker.getProcessedFiles())
                .failedFiles(tracker.getFailedFiles())
                .message(tracker.getMessage())
                .uploadedFilename(tracker.getUploadedFilename())
                .createdAt(tracker.getCreatedAt())
                .updatedAt(tracker.getUpdatedAt())
                .completedAt(tracker.getCompletedAt())
                .build();
    }
}
