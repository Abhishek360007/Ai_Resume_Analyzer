package com.abhishek.resumeanalyzer.pekko;

import com.abhishek.resumeanalyzer.models.JobStatus;
import com.abhishek.resumeanalyzer.models.JobType;
import com.abhishek.resumeanalyzer.pekko.actors.JobPollingActor;
import com.abhishek.resumeanalyzer.pekko.actors.ResumeJobSupervisor;
import com.abhishek.resumeanalyzer.services.JobQueueService;
import com.abhishek.resumeanalyzer.services.ResumeJobProcessor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Props;
import org.apache.pekko.stream.Materializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * Owns the lifecycle of the Apache Pekko resume processing pipeline and exposes queue
 * health/stats to the monitoring endpoints.
 *
 * <p>On startup it spawns the {@link ResumeJobSupervisor} (actor worker pool) and the
 * {@link JobPollingActor} (Pekko Streams poller). On shutdown it stops the stream and
 * terminates the actor system.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.pekko.enabled", havingValue = "true", matchIfMissing = true)
public class ResumeProcessingEngine {

    private final ActorSystem<Void> resumeActorSystem;
    private final Materializer resumeMaterializer;
    private final ResumeJobProcessor resumeJobProcessor;
    private final JobQueueService jobQueueService;

    private final ResumeProcessingStats stats = new ResumeProcessingStats();

    @Value("${app.pekko.worker-count:5}")
    private int workerCount;

    @Value("${app.pekko.poll-interval-ms:5000}")
    private long pollIntervalMs;

    @Value("${app.pekko.initial-delay-ms:10000}")
    private long initialDelayMs;

    @Value("${app.pekko.claim-batch-size:5}")
    private int claimBatchSize;

    @Value("${app.pekko.mailbox-capacity:100}")
    private int queueCapacity;

    private ActorRef<ResumeJobSupervisor.Command> supervisor;
    private ActorRef<JobPollingActor.Command> poller;

    public ResumeProcessingEngine(ActorSystem<Void> resumeActorSystem,
                                  Materializer resumeMaterializer,
                                  ResumeJobProcessor resumeJobProcessor,
                                  JobQueueService jobQueueService) {
        this.resumeActorSystem = resumeActorSystem;
        this.resumeMaterializer = resumeMaterializer;
        this.resumeJobProcessor = resumeJobProcessor;
        this.jobQueueService = jobQueueService;
    }

    @PostConstruct
    public void start() {
        log.info("Starting Pekko resume processing engine: workers={}, pollIntervalMs={}, claimBatch={}, queueCapacity={}",
                workerCount, pollIntervalMs, claimBatchSize, queueCapacity);

        supervisor = resumeActorSystem.systemActorOf(
                ResumeJobSupervisor.create(resumeJobProcessor, stats, workerCount),
                "resume-job-supervisor", Props.empty());

        poller = resumeActorSystem.systemActorOf(
                JobPollingActor.create(jobQueueService, supervisor, resumeMaterializer,
                        Duration.ofMillis(initialDelayMs), Duration.ofMillis(pollIntervalMs),
                        claimBatchSize, queueCapacity),
                "resume-job-poller", Props.empty());

        log.info("Pekko resume processing engine started");
    }

    @PreDestroy
    public void stop() {
        log.info("Stopping Pekko resume processing engine");
        if (poller != null) {
            poller.tell(new JobPollingActor.Stop());
        }
        resumeActorSystem.terminate();
    }

    /**
     * Number of jobs currently being processed by the worker pool.
     */
    public int getActiveJobCount() {
        return stats.getInFlight();
    }

    public long getProcessedCount() {
        return stats.getProcessedCount();
    }

    public long getFailedCount() {
        return stats.getFailedCount();
    }

    public ResumeProcessingStats getStats() {
        return stats;
    }

    /**
     * Queue health summary for the monitoring endpoint.
     */
    public Map<String, Object> getQueueHealth() {
        return Map.of(
                "activeJobs", stats.getInFlight(),
                "pendingJobs", jobQueueService.getQueueDepth(JobType.RESUME_PROCESSING),
                "processingJobs", jobQueueService.getJobCount(JobStatus.PROCESSING),
                "averageProcessingTime", jobQueueService.getAverageProcessingDuration(JobType.RESUME_PROCESSING)
        );
    }
}
