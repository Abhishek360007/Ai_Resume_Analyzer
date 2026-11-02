package com.abhishek.resumeanalyzer.services;

import com.abhishek.resumeanalyzer.entities.Candidate;
import com.abhishek.resumeanalyzer.entities.JobQueue;
import com.abhishek.resumeanalyzer.models.CandidateStatus;
import com.abhishek.resumeanalyzer.models.EmploymentEntry;
import com.abhishek.resumeanalyzer.models.ProcessStatus;
import com.abhishek.resumeanalyzer.models.ResumeAnalysisRequest;
import com.abhishek.resumeanalyzer.models.ResumeAnalysisResponse;
import com.abhishek.resumeanalyzer.models.TrackerStatusEvent;
import com.abhishek.resumeanalyzer.exceptions.LlmServiceException;
import com.abhishek.resumeanalyzer.repos.CandidateRepository;
import com.abhishek.resumeanalyzer.repos.ProcessTrackerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Processor for resume processing jobs.
 * Handles the actual resume parsing, AI analysis, embedding generation, and storage.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeJobProcessor {

    private final FileParserService fileParserService;
    private final AIService aiService;
    private final EmbeddingService embeddingService;
    private final CandidateRepository candidateRepository;
    private final ProcessTrackerRepository trackerRepository;
    private final JobQueueService jobQueueService;
    private final TrackerEventPublisher trackerEventPublisher;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    /**
     * Process a resume job from the queue.
     *
     * <p>All work runs in one transaction, so a failure part-way through (e.g. the
     * embedding call) leaves no half-created candidate behind. Failure bookkeeping
     * runs only after that transaction has rolled back; recording it inside the
     * doomed transaction would roll it back too and leave the job stuck in PROCESSING.</p>
     *
     * @param job The job to process
     */
    public void processJob(JobQueue job) {
        UUID jobId = job.getId();
        log.info("Starting resume job processing: jobId={}, priority={}, retryCount={}", 
                 jobId, job.getPriority(), job.getRetryCount());

        try {
            transactionTemplate.executeWithoutResult(status -> processInTransaction(job));
        } catch (Exception e) {
            log.error("Error processing resume job: jobId={}, error={}", jobId, e.getMessage(), e);
            handleJobFailure(job, e);
        }
    }

    private void processInTransaction(JobQueue job) {
        UUID jobId = job.getId();
        {
            // Extract metadata
            Map<String, Object> metadata = job.getMetadata();
            if (metadata == null) {
                throw new IllegalStateException("Job metadata is null");
            }

            String filename = (String) metadata.get("filename");
            String trackerIdStr = (String) metadata.get("trackerId");
            
            if (filename == null || trackerIdStr == null) {
                throw new IllegalStateException("Missing required metadata: filename or trackerId");
            }

            UUID trackerId = UUID.fromString(trackerIdStr);
            byte[] fileData = job.getFileData();

            log.info("Processing resume job: jobId={}, filename={}, trackerId={}, dataSize={} bytes", 
                     jobId, filename, trackerId, fileData != null ? fileData.length : 0);

            // Link job to tracker. Tracker writes are atomic queries rather than entity saves:
            // several workers update the same batch tracker concurrently, and saving a stale
            // entity copy would overwrite the processed/failed counters written by the others.
            if (trackerRepository.linkJob(trackerId, jobId, job.getCorrelationId()) == 0) {
                throw new IllegalArgumentException("Tracker not found: " + trackerId);
            }

            // Send heartbeat before starting
            jobQueueService.updateHeartbeat(jobId);

            // Step 1: Extract text from file
            log.debug("Extracting text from resume: jobId={}, filename={}", jobId, filename);
            String resumeContent;
            try {
                resumeContent = fileParserService.extractText(fileData, filename);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            log.info("Text extraction complete: jobId={}, contentLength={} chars", 
                     jobId, resumeContent.length());

            // Send heartbeat after parsing
            jobQueueService.updateHeartbeat(jobId);

            // Step 2: Analyze resume using AI
            log.debug("Analyzing resume with AI: jobId={}, filename={}", jobId, filename);
            ResumeAnalysisRequest analysisRequest = ResumeAnalysisRequest.builder()
                    .resumeContent(resumeContent)
                    .filename(filename)
                    .build();

            ResumeAnalysisResponse analysisResponse = aiService.analyzeResume(analysisRequest);
            if (analysisResponse.isFallback()) {
                // Never save an "Unknown" placeholder candidate; let the job retry and then fail visibly
                throw new LlmServiceException("AI analysis unavailable for " + filename + ": "
                        + (analysisResponse.getFailureReason() != null ? analysisResponse.getFailureReason()
                                : "check the AI provider connection and API key"));
            }
            log.info("AI analysis complete: jobId={}, candidateName={}, skillsPresent={}", 
                     jobId, analysisResponse.getName(), 
                     analysisResponse.getSkills() != null && !analysisResponse.getSkills().isEmpty());

            reportStage(trackerId, ProcessStatus.RESUME_ANALYZED, "Resume analyzed: " + filename);

            // Send heartbeat after AI analysis
            jobQueueService.updateHeartbeat(jobId);

            // Step 3: Create candidate entity
            log.debug("Creating candidate entity: jobId={}, name={}", jobId, analysisResponse.getName());
            Candidate candidate = Candidate.builder()
                    .name(analysisResponse.getName())
                    .email(analysisResponse.getEmail())
                    .mobile(analysisResponse.getMobile())
                    .resumeFilename(filename)
                    .resumeContent(resumeContent)
                    .resumeFile(fileData)
                    .experienceSummary(analysisResponse.getExperienceSummary())
                    .skills(analysisResponse.getSkills())
                    .domainKnowledge(analysisResponse.getDomainKnowledge())
                    .academicBackground(analysisResponse.getAcademicBackground())
                    .yearsOfExperience(analysisResponse.getYearsOfExperience())
                    .workHistory(serializeWorkHistory(analysisResponse.getWorkHistory()))
                    .linkedInUrl(trimToNull(analysisResponse.getLinkedInUrl()))
                    .githubUrl(trimToNull(analysisResponse.getGithubUrl()))
                    .twitterUrl(trimToNull(analysisResponse.getTwitterUrl()))
                    .currentCompany(deriveCurrentCompany(analysisResponse.getWorkHistory()))
                    .status(CandidateStatus.PENDING_CONFIRMATION)
                    .build();

            candidate = candidateRepository.save(candidate);
            log.info("Candidate saved awaiting confirmation: jobId={}, candidateId={}, name={}",
                     jobId, candidate.getId(), candidate.getName());

            // Send heartbeat after saving candidate
            jobQueueService.updateHeartbeat(jobId);

            // Step 4: Generate embeddings
            log.debug("Generating embeddings: jobId={}, candidateId={}", jobId, candidate.getId());
            embeddingService.generateAndStoreEmbeddings(candidate, resumeContent);
            log.info("Embeddings generated and stored: jobId={}, candidateId={}", 
                     jobId, candidate.getId());

            reportStage(trackerId, ProcessStatus.VECTOR_DB_UPDATED, "Search index updated: " + filename);

            // Prepare result metadata
            Map<String, Object> result = new HashMap<>();
            result.put("candidateId", candidate.getId().toString());
            result.put("candidateName", candidate.getName());
            result.put("filename", filename);
            result.put("trackerId", trackerId.toString());
            result.put("skillsPresent", analysisResponse.getSkills() != null && !analysisResponse.getSkills().isEmpty());
            result.put("yearsOfExperience", analysisResponse.getYearsOfExperience());
            result.put("awaitingConfirmation", true);

            // Mark job as completed
            jobQueueService.markJobCompleted(jobId, result);

            // Record this file as processed; batch-level COMPLETED only when all files are done
            trackerRepository.recordProcessedFile(
                    trackerId, ProcessStatus.COMPLETED, "Resume analyzed — awaiting confirmation");
            publishTracker(trackerId, TrackerStatusEvent.TYPE_PROCESSED);

            log.info("Resume job processing completed successfully: jobId={}, candidateId={}, duration={}s", 
                     jobId, candidate.getId(), 
                     job.getStartedAt() != null ? 
                         java.time.Duration.between(job.getStartedAt(), java.time.LocalDateTime.now()).getSeconds() : 0);

        }
    }

    private void reportStage(UUID trackerId, ProcessStatus status, String message) {
        trackerRepository.updateStage(trackerId, status, message);
        publishTracker(trackerId, TrackerStatusEvent.TYPE_UPDATE);
    }

    /**
     * Publish the freshest tracker state after an atomic bulk update
     * (per-file progress / failures), since the in-memory tracker is stale
     * after those queries run.  Failures to publish are logged and never
     * interrupt resume processing.
     */
    private void publishTracker(UUID trackerId, String eventType) {
        try {
            trackerRepository.findCurrentById(trackerId)
                    .ifPresent(tracker -> trackerEventPublisher.publish(tracker, eventType));
        } catch (Exception e) {
            log.warn("Failed to publish tracker event {} for {}: {}", eventType, trackerId, e.getMessage());
        }
    }

    /**
     * Handle job failure by updating status and determining if retry is needed.
     * 
     * @param job The failed job
     * @param error The exception that caused the failure
     */
    private void handleJobFailure(JobQueue job, Exception error) {
        UUID jobId = job.getId();
        String errorMessage = error.getMessage();
        
        log.warn("Handling job failure: jobId={}, retryCount={}/{}, error={}", 
                 jobId, job.getRetryCount(), job.getMaxRetries(), errorMessage);

        // Determine if the error is retryable
        boolean shouldRetry = isRetryableError(error);
        
        if (!shouldRetry) {
            log.error("Non-retryable error encountered: jobId={}, errorType={}", 
                     jobId, error.getClass().getSimpleName());
        }

        // Mark job as failed (service will handle retry logic)
        jobQueueService.markJobFailed(jobId, errorMessage, shouldRetry);

        // Update process tracker
        try {
            Map<String, Object> metadata = job.getMetadata();
            if (metadata != null && metadata.containsKey("trackerId")) {
                String trackerIdStr = (String) metadata.get("trackerId");
                UUID trackerId = UUID.fromString(trackerIdStr);

                boolean willRetry = shouldRetry && job.canRetry();
                if (willRetry) {
                    String retryMessage = String.format("Processing failed, will retry (attempt %d/%d): %s",
                            job.getRetryCount() + 1, job.getMaxRetries(), errorMessage);
                    trackerRepository.updateTrackerMessage(trackerId, retryMessage);
                    publishTracker(trackerId, TrackerStatusEvent.TYPE_UPDATE);
                    log.debug("Updated process tracker message (will retry): trackerId={}", trackerId);
                } else {
                    String failureMessage = String.format("Processing failed permanently: %s", errorMessage);
                    trackerRepository.recordFailedFile(
                            trackerId, ProcessStatus.FAILED, failureMessage);
                    publishTracker(trackerId, TrackerStatusEvent.TYPE_FAILED);
                    log.debug("Recorded failed file on tracker: trackerId={}", trackerId);
                }
            }
        } catch (Exception e) {
            log.error("Error updating process tracker after job failure: jobId={}", jobId, e);
        }
    }

    /**
     * Determine if an error is retryable.
     * Transient errors (network issues, timeouts) should be retried.
     * Permanent errors (invalid data, parsing errors) should not be retried.
     * 
     * @param error The exception to check
     * @return true if the error is retryable
     */
    private boolean isRetryableError(Exception error) {
        // Check exception type
        String errorClass = error.getClass().getSimpleName();
        String errorMessage = error.getMessage() != null ? error.getMessage().toLowerCase() : "";

        // Non-retryable errors
        if (errorClass.contains("IllegalArgument") || errorClass.contains("IllegalState")) {
            return false;
        }
        
        if (errorMessage.contains("invalid") || errorMessage.contains("malformed") || 
            errorMessage.contains("unsupported")) {
            return false;
        }

        // Retryable errors (network, timeout, temporary issues)
        if (errorClass.contains("Timeout") || errorClass.contains("Connection") || 
            errorClass.contains("Socket") || errorClass.contains("IO")) {
            return true;
        }
        
        if (errorMessage.contains("timeout") || errorMessage.contains("connection") || 
            errorMessage.contains("network") || errorMessage.contains("unavailable")) {
            return true;
        }

        // Default: retry for unknown errors
        log.debug("Unknown error type, defaulting to retryable: {}", errorClass);
        return true;
    }

    /**
     * Serialise the LLM-extracted employment history into a JSON string for storage.
     * Returns {@code null} when there is no work history.
     */
    private String serializeWorkHistory(List<EmploymentEntry> workHistory) {
        if (workHistory == null || workHistory.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(workHistory);
        } catch (Exception e) {
            log.warn("Failed to serialise work history to JSON: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Derive the candidate's most recent company from the work history
     * (the entry with a null end year, or the latest start year).
     */
    private String deriveCurrentCompany(List<EmploymentEntry> workHistory) {
        if (workHistory == null || workHistory.isEmpty()) {
            return null;
        }
        return workHistory.stream()
                .filter(e -> e != null && e.getCompany() != null)
                .min((a, b) -> {
                    boolean aCurrent = a.getEndYear() == null;
                    boolean bCurrent = b.getEndYear() == null;
                    if (aCurrent && !bCurrent) return -1;
                    if (!aCurrent && bCurrent) return 1;
                    int aStart = a.getStartYear() != null ? a.getStartYear() : 0;
                    int bStart = b.getStartYear() != null ? b.getStartYear() : 0;
                    return Integer.compare(bStart, aStart);
                })
                .map(EmploymentEntry::getCompany)
                .orElse(null);
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed) || "none".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed;
    }
}
