package com.abhishek.resumeanalyzer.repos;

import com.abhishek.resumeanalyzer.entities.ProcessTracker;
import com.abhishek.resumeanalyzer.models.ProcessStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for ProcessTracker entity operations.
 * Manages tracking records for async resume processing jobs.
 */
@Repository
public interface ProcessTrackerRepository extends JpaRepository<ProcessTracker, UUID> {

    /**
     * Find process tracker by status
     * 
     * @param status The process status to filter by
     * @return List of process trackers with the specified status
     */
    List<ProcessTracker> findByStatus(ProcessStatus status);

    /**
     * Find process trackers created after a specific date
     * 
     * @param dateTime The cutoff datetime
     * @return List of recent process trackers
     */
    List<ProcessTracker> findByCreatedAtAfter(LocalDateTime dateTime);

    /**
     * Find the most recent process tracker for a file
     * 
     * @param filename The uploaded filename
     * @return Optional containing the latest tracker for the file
     */
    Optional<ProcessTracker> findFirstByUploadedFilenameOrderByCreatedAtDesc(String filename);

    /**
     * Find incomplete (not completed or failed) process trackers
     * 
     * @param statuses List of statuses to exclude
     * @return List of in-progress trackers
     */
    List<ProcessTracker> findByStatusNotIn(List<ProcessStatus> statuses);

    /**
     * Find all process trackers ordered by creation date descending
     * 
     * @return List of all process trackers sorted by most recent first
     */
    @Query("SELECT pt FROM ProcessTracker pt ORDER BY pt.createdAt DESC")
    List<ProcessTracker> findAllOrderByCreatedAtDesc();

    /**
     * Atomically record a successfully processed file and complete the batch when all files are done.
     * Uses a single bulk UPDATE (row-locked) so concurrent workers cannot lose increments.
     *
     * @param trackerId The tracker id to update
     * @return the number of rows updated (1 if the tracker exists)
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE ProcessTracker t SET
              t.status = CASE WHEN COALESCE(t.processedFiles, 0) + 1 + COALESCE(t.failedFiles, 0) >= COALESCE(t.totalFiles, 0)
                              THEN :completedStatus ELSE t.status END,
              t.message = CASE WHEN COALESCE(t.processedFiles, 0) + 1 + COALESCE(t.failedFiles, 0) >= COALESCE(t.totalFiles, 0)
                               THEN :completedMessage ELSE t.message END,
              t.completedAt = CASE WHEN COALESCE(t.processedFiles, 0) + 1 + COALESCE(t.failedFiles, 0) >= COALESCE(t.totalFiles, 0)
                                   THEN CURRENT_TIMESTAMP ELSE t.completedAt END,
              t.updatedAt = CURRENT_TIMESTAMP,
              t.processedFiles = COALESCE(t.processedFiles, 0) + 1
            WHERE t.id = :trackerId
            """)
    int recordProcessedFile(@Param("trackerId") UUID trackerId,
                            @Param("completedStatus") ProcessStatus completedStatus,
                            @Param("completedMessage") String completedMessage);

    /**
     * Atomically record a permanently failed file and complete the batch when all files are done.
     *
     * @param trackerId The tracker id to update
     * @param failedStatus The status to set when the batch is complete (typically {@code ProcessStatus.FAILED})
     * @param failureMessage The failure message to set when the batch is complete
     * @return the number of rows updated (1 if the tracker exists)
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE ProcessTracker t SET
              t.status = CASE WHEN COALESCE(t.processedFiles, 0) + COALESCE(t.failedFiles, 0) + 1 >= COALESCE(t.totalFiles, 0)
                              THEN :failedStatus ELSE t.status END,
              t.message = CASE WHEN COALESCE(t.processedFiles, 0) + COALESCE(t.failedFiles, 0) + 1 >= COALESCE(t.totalFiles, 0)
                               THEN :failureMessage ELSE t.message END,
              t.completedAt = CASE WHEN COALESCE(t.processedFiles, 0) + COALESCE(t.failedFiles, 0) + 1 >= COALESCE(t.totalFiles, 0)
                                   THEN CURRENT_TIMESTAMP ELSE t.completedAt END,
              t.updatedAt = CURRENT_TIMESTAMP,
              t.failedFiles = COALESCE(t.failedFiles, 0) + 1
            WHERE t.id = :trackerId
            """)
    int recordFailedFile(@Param("trackerId") UUID trackerId,
                         @Param("failedStatus") ProcessStatus failedStatus,
                         @Param("failureMessage") String failureMessage);

    /**
     * Update only the tracker message (non-terminal) for a transient failure that will be retried.
     *
     * @param trackerId The tracker id to update
     * @param message The new message
     * @return the number of rows updated (1 if the tracker exists)
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE ProcessTracker t SET
              t.message = :message,
              t.updatedAt = CURRENT_TIMESTAMP
            WHERE t.id = :trackerId
            """)
    int updateTrackerMessage(@Param("trackerId") UUID trackerId, @Param("message") String message);

    /*
     * The methods below run in their own short transaction (REQUIRES_NEW) because they are
     * called from inside a long-running resume job: they must be visible to the UI right away,
     * and must not hold the shared tracker row lock while the job waits on the AI provider.
     */

    /**
     * Record which job and correlation id last touched the tracker, without rewriting counters.
     *
     * @return the number of rows updated
     */
    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("""
            UPDATE ProcessTracker t SET
              t.jobId = :jobId,
              t.correlationId = :correlationId,
              t.updatedAt = CURRENT_TIMESTAMP
            WHERE t.id = :trackerId
            """)
    int linkJob(@Param("trackerId") UUID trackerId,
                @Param("jobId") UUID jobId,
                @Param("correlationId") String correlationId);

    /**
     * Report an intermediate processing stage. Only status and message change, so per-file
     * counters written concurrently by other workers are never overwritten, and a batch that
     * has already finished keeps its final status.
     *
     * @return the number of rows updated (0 if the batch already completed or failed)
     */
    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("""
            UPDATE ProcessTracker t SET
              t.status = :status,
              t.message = :message,
              t.updatedAt = CURRENT_TIMESTAMP
            WHERE t.id = :trackerId
              AND t.status NOT IN (com.abhishek.resumeanalyzer.models.ProcessStatus.COMPLETED,
                                   com.abhishek.resumeanalyzer.models.ProcessStatus.FAILED)
            """)
    int updateStage(@Param("trackerId") UUID trackerId,
                    @Param("status") ProcessStatus status,
                    @Param("message") String message);

    /**
     * Read the tracker as currently committed, bypassing any stale copy cached in the
     * caller's persistence context.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    @Query("SELECT t FROM ProcessTracker t WHERE t.id = :trackerId")
    Optional<ProcessTracker> findCurrentById(@Param("trackerId") UUID trackerId);
}
