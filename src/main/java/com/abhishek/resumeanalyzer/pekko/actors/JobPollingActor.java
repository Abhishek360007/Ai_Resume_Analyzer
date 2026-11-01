package com.abhishek.resumeanalyzer.pekko.actors;

import com.abhishek.resumeanalyzer.entities.JobQueue;
import com.abhishek.resumeanalyzer.models.JobType;
import com.abhishek.resumeanalyzer.services.JobQueueService;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.stream.ActorAttributes;
import org.apache.pekko.stream.KillSwitches;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.OverflowStrategy;
import org.apache.pekko.stream.Supervision;
import org.apache.pekko.stream.UniqueKillSwitch;
import org.apache.pekko.stream.javadsl.Keep;
import org.apache.pekko.stream.javadsl.RestartSource;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Polls the persistent job queue and feeds the {@link ResumeJobSupervisor} worker pool.
 *
 * <p>This is the Pekko Streams half of the pipeline: a resilient tick source claims
 * pending jobs (bounded by {@code claimBatchSize}) and streams them into the actor pool.
 * Backpressure is provided by a bounded {@link OverflowStrategy#backpressure} buffer, and
 * resilience by {@link RestartSource} + a resuming supervision decider for transient
 * database errors.
 */
public class JobPollingActor extends AbstractBehavior<JobPollingActor.Command> {

    public sealed interface Command permits Stop {
    }

    public record Stop() implements Command {
    }

    private final JobQueueService jobQueueService;
    private final ActorRef<ResumeJobSupervisor.Command> supervisorRef;
    private final Materializer materializer;
    private final Duration initialDelay;
    private final Duration interval;
    private final int claimBatchSize;
    private final int queueCapacity;
    private UniqueKillSwitch killSwitch;

    private JobPollingActor(ActorContext<Command> context,
                            JobQueueService jobQueueService,
                            ActorRef<ResumeJobSupervisor.Command> supervisorRef,
                            Materializer materializer,
                            Duration initialDelay,
                            Duration interval,
                            int claimBatchSize,
                            int queueCapacity) {
        super(context);
        this.jobQueueService = jobQueueService;
        this.supervisorRef = supervisorRef;
        this.materializer = materializer;
        this.initialDelay = initialDelay;
        this.interval = interval;
        this.claimBatchSize = claimBatchSize;
        this.queueCapacity = queueCapacity;
    }

    public static Behavior<Command> create(JobQueueService jobQueueService,
                                           ActorRef<ResumeJobSupervisor.Command> supervisorRef,
                                           Materializer materializer,
                                           Duration initialDelay,
                                           Duration interval,
                                           int claimBatchSize,
                                           int queueCapacity) {
        return Behaviors.setup(ctx -> {
            JobPollingActor actor = new JobPollingActor(ctx, jobQueueService, supervisorRef,
                    materializer, initialDelay, interval, claimBatchSize, queueCapacity);
            actor.startStream();
            return actor;
        });
    }

    private void startStream() {
        Source<JobQueue, NotUsed> pollSource = RestartSource.withBackoff(
                Duration.ofMillis(100), Duration.ofSeconds(30), 0.2,
                () -> Source.tick(initialDelay, interval, NotUsed.getInstance())
                        .mapAsync(1, tick -> CompletableFuture.completedFuture(
                                jobQueueService.claimJobs(JobType.RESUME_PROCESSING, claimBatchSize)))
                        .withAttributes(ActorAttributes.withSupervisionStrategy(Supervision.getResumingDecider()))
                        .mapConcat(list -> list)
                        .buffer(queueCapacity, OverflowStrategy.backpressure()));

        killSwitch = pollSource
                .viaMat(KillSwitches.single(), Keep.right())
                .to(Sink.foreach(job -> supervisorRef.tell(new ResumeJobSupervisor.ProcessJob(job))))
                .run(materializer);

        getContext().getLog().info("Resume job polling stream started: interval={}, claimBatch={}, queueCapacity={}",
                interval, claimBatchSize, queueCapacity);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(Stop.class, this::onStop)
                .build();
    }

    private Behavior<Command> onStop(Stop stop) {
        getContext().getLog().info("Stopping resume job polling stream");
        if (killSwitch != null) {
            killSwitch.shutdown();
        }
        return Behaviors.stopped();
    }
}
