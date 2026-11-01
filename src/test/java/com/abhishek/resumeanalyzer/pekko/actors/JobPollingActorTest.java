package com.abhishek.resumeanalyzer.pekko.actors;

import com.abhishek.resumeanalyzer.entities.JobQueue;
import com.abhishek.resumeanalyzer.models.JobPriority;
import com.abhishek.resumeanalyzer.models.JobStatus;
import com.abhishek.resumeanalyzer.models.JobType;
import com.abhishek.resumeanalyzer.services.JobQueueService;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.SystemMaterializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobPollingActorTest {

    private ActorTestKit testKit;
    private JobQueueService jobQueueService;

    @BeforeEach
    void setUp() {
        testKit = ActorTestKit.create();
        jobQueueService = mock(JobQueueService.class);
    }

    @AfterEach
    void tearDown() {
        testKit.shutdownTestKit();
    }

    @Test
    void claimsJobsAndDispatchesToSupervisor() {
        Materializer materializer = SystemMaterializer.get(testKit.system()).materializer();
        JobQueue job = createJob();
        when(jobQueueService.claimJobs(JobType.RESUME_PROCESSING, 5)).thenReturn(List.of(job));

        TestProbe<ResumeJobSupervisor.Command> supervisorProbe =
                testKit.createTestProbe(ResumeJobSupervisor.Command.class);
        ActorRef<JobPollingActor.Command> poller = testKit.spawn(
                JobPollingActor.create(jobQueueService, supervisorProbe.ref(), materializer,
                        Duration.ofMillis(100), Duration.ofSeconds(30), 5, 10),
                "resume-poller");

        ResumeJobSupervisor.ProcessJob dispatched =
                supervisorProbe.expectMessageClass(ResumeJobSupervisor.ProcessJob.class);
        assertEquals(job.getId(), dispatched.job().getId());
        verify(jobQueueService).claimJobs(JobType.RESUME_PROCESSING, 5);

        poller.tell(new JobPollingActor.Stop());
    }

    @Test
    void continuesPollingWhenNoJobsAreAvailable() {
        Materializer materializer = SystemMaterializer.get(testKit.system()).materializer();
        when(jobQueueService.claimJobs(JobType.RESUME_PROCESSING, 5)).thenReturn(List.of());

        TestProbe<ResumeJobSupervisor.Command> supervisorProbe =
                testKit.createTestProbe(ResumeJobSupervisor.Command.class);
        ActorRef<JobPollingActor.Command> poller = testKit.spawn(
                JobPollingActor.create(jobQueueService, supervisorProbe.ref(), materializer,
                        Duration.ofMillis(100), Duration.ofMillis(200), 5, 10),
                "resume-poller");

        // Empty polls must not fail the stream; the actor should stay alive across ticks.
        verify(jobQueueService, timeout(3000).atLeast(2)).claimJobs(JobType.RESUME_PROCESSING, 5);

        poller.tell(new JobPollingActor.Stop());
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
