package com.abhishek.resumeanalyzer.pekko;

import com.abhishek.resumeanalyzer.models.JobStatus;
import com.abhishek.resumeanalyzer.models.JobType;
import com.abhishek.resumeanalyzer.services.JobQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Housekeeping tasks for the persistent job queue that run alongside the Pekko pipeline:
 * stale job recovery, old-job cleanup and periodic queue metrics logging.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.pekko.enabled", havingValue = "true", matchIfMissing = true)
public class JobQueueMaintenanceService {

    private final JobQueueService jobQueueService;
    private final ResumeProcessingEngine resumeProcessingEngine;

    @Value("${app.pekko.cleanup-retention-days:30}")
    private int cleanupRetentionDays;

    @Scheduled(fixedDelayString = "${app.pekko.stale-check-interval-ms:60000}",
               initialDelayString = "30000")
    public void checkForStaleJobs() {
        try {
            int resetCount = jobQueueService.resetStaleJobs();
            if (resetCount > 0) {
                log.warn("Stale job check completed: reset {} stale jobs", resetCount);
            } else {
                log.debug("Stale job check completed: no stale jobs found");
            }
        } catch (Exception e) {
            log.error("Error checking for stale jobs: {}", e.getMessage(), e);
        }
    }

    @Scheduled(cron = "${app.pekko.cleanup-cron:0 0 2 * * ?}") // Default: 2 AM daily
    public void cleanupOldJobs() {
        try {
            log.info("Running cleanup of old completed jobs (keeping last {} days)", cleanupRetentionDays);
            int deletedCount = jobQueueService.cleanupOldJobs(cleanupRetentionDays);
            if (deletedCount > 0) {
                log.info("Cleanup completed: deleted {} old completed jobs", deletedCount);
            } else {
                log.debug("Cleanup completed: no old jobs to delete");
            }
        } catch (Exception e) {
            log.error("Error during job cleanup: {}", e.getMessage(), e);
        }
    }

    @Scheduled(fixedDelayString = "${app.pekko.metrics-log-interval-ms:300000}") // Default: 5 minutes
    public void logQueueMetrics() {
        try {
            long pendingCount = jobQueueService.getQueueDepth(JobType.RESUME_PROCESSING);
            long processingCount = jobQueueService.getJobCount(JobStatus.PROCESSING);
            long completedCount = jobQueueService.getJobCount(JobStatus.COMPLETED);
            long failedCount = jobQueueService.getJobCount(JobStatus.FAILED);
            double avgDuration = jobQueueService.getAverageProcessingDuration(JobType.RESUME_PROCESSING);
            String avgDurationStr = String.format("%.2f", avgDuration);

            log.info("Queue Metrics - Pending: {}, Processing: {}, Active Tasks: {}, Completed: {}, Failed: {}, " +
                            "Processed By Pekko: {}, Failed By Pekko: {}, Avg Duration: {}s",
                    pendingCount, processingCount, resumeProcessingEngine.getActiveJobCount(),
                    completedCount, failedCount,
                    resumeProcessingEngine.getProcessedCount(), resumeProcessingEngine.getFailedCount(),
                    avgDurationStr);
        } catch (Exception e) {
            log.error("Error logging queue metrics: {}", e.getMessage(), e);
        }
    }
}
