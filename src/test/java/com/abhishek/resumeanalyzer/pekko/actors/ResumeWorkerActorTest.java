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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ResumeWorkerActorTest {

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
    void processesJobAndAcknowledgesToParent() {
        TestProbe<ResumeJobSupervisor.Command> parent = testKit.createTestProbe(ResumeJobSupervisor.Command.class);
        ActorRef<ResumeWorkerActor.Command> worker = testKit.spawn(
                ResumeWorkerActor.create(resumeJobProcessor, stats, parent.ref()), "resume-worker");

        JobQueue job = createJob();
        worker.tell(new ResumeWorkerActor.ProcessJob(job));

        parent.expectMessageClass(ResumeJobSupervisor.JobDone.class);
        verify(resumeJobProcessor).processJob(job);
        assertEquals(1, stats.getProcessedCount());
        assertEquals(0, stats.getFailedCount());
    }

    @Test
    void reportsFailureToParent() {
        TestProbe<ResumeJobSupervisor.Command> parent = testKit.createTestProbe(ResumeJobSupervisor.Command.class);
        ActorRef<ResumeWorkerActor.Command> worker = testKit.spawn(
                ResumeWorkerActor.create(resumeJobProcessor, stats, parent.ref()), "resume-worker");

        JobQueue job = createJob();
        doThrow(new IllegalStateException("boom")).when(resumeJobProcessor).processJob(job);
        worker.tell(new ResumeWorkerActor.ProcessJob(job));

        ResumeJobSupervisor.JobFailed failed = parent.expectMessageClass(ResumeJobSupervisor.JobFailed.class);
        assertEquals(job.getId(), failed.jobId());
        assertEquals(0, stats.getProcessedCount());
        assertEquals(1, stats.getFailedCount());
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
