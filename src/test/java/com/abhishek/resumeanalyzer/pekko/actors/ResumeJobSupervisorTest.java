package com.abhishek.resumeanalyzer.pekko.actors;

import com.abhishek.resumeanalyzer.entities.JobQueue;
import com.abhishek.resumeanalyzer.models.JobPriority;
import com.abhishek.resumeanalyzer.models.JobStatus;
import com.abhishek.resumeanalyzer.models.JobType;
import com.abhishek.resumeanalyzer.pekko.ResumeProcessingStats;
import com.abhishek.resumeanalyzer.services.ResumeJobProcessor;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class ResumeJobSupervisorTest {

    private ActorTestKit testKit;
    private ResumeJobProcessor resumeJobProcessor;
    private ResumeProcessingStats stats;

    @BeforeEach
    void setUp() {
        testKit = ActorTestKit.create();
        resumeJobProcessor = mock(ResumeJobProcessor.class);
        stats = new ResumeProcessingStats();
    }

    @AfterEach
    void tearDown() {
        testKit.shutdownTestKit();
    }

    @Test
    void routesJobsToWorkersAndTracksCompletion() {
        ActorRef<ResumeJobSupervisor.Command> supervisor = testKit.spawn(
                ResumeJobSupervisor.create(resumeJobProcessor, stats, 2), "resume-supervisor");

        JobQueue job1 = createJob();
        JobQueue job2 = createJob();
        JobQueue job3 = createJob();

        supervisor.tell(new ResumeJobSupervisor.ProcessJob(job1));
        supervisor.tell(new ResumeJobSupervisor.ProcessJob(job2));
        supervisor.tell(new ResumeJobSupervisor.ProcessJob(job3));

        verify(resumeJobProcessor, timeout(5000).times(3)).processJob(any(JobQueue.class));
        awaitUntil(() -> stats.getInFlight() == 0 && stats.getProcessedCount() == 3);
        assertEquals(0, stats.getFailedCount());
    }

    @Test
    void exposesStatsToProbe() {
        ActorRef<ResumeJobSupervisor.Command> supervisor = testKit.spawn(
                ResumeJobSupervisor.create(resumeJobProcessor, stats, 1), "resume-supervisor");

        TestProbe<ResumeProcessingStats> probe = testKit.createTestProbe(ResumeProcessingStats.class);
        supervisor.tell(new ResumeJobSupervisor.GetStats(probe.ref()));

        assertSame(stats, probe.expectMessageClass(ResumeProcessingStats.class));
    }

    private void awaitUntil(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 5000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail("Condition not met within timeout");
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrupted while awaiting condition");
            }
        }
    }

    private JobQueue createJob() {
        return JobQueue.builder()
                .id(UUID.randomUUID())
                .jobType(JobType.RESUME_PROCESSING)
                .status(JobStatus.PENDING)
                .priority(JobPriority.NORMAL.getValue())
                .correlationId("test-correlation")
                .retryCount(0)
                .maxRetries(3)
                .build();
    }
}
