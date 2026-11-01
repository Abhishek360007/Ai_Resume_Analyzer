package com.abhishek.resumeanalyzer.pekko.actors;

import com.abhishek.resumeanalyzer.entities.JobQueue;
import com.abhishek.resumeanalyzer.pekko.ResumeProcessingStats;
import com.abhishek.resumeanalyzer.services.ResumeJobProcessor;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.SupervisorStrategy;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Supervises the pool of {@link ResumeWorkerActor}s and routes resume jobs to them
 * round-robin. The number of workers bounds concurrency; each worker is supervised
 * with restart-with-backoff so a crashed worker recovers without taking down the pool.
 *
 * <p>In-flight accounting is done here: {@link ResumeProcessingStats#onDispatch()} when a
 * job is routed to a worker, and {@link ResumeProcessingStats#onCompletion()} when the
 * worker acknowledges the result.
 */
public class ResumeJobSupervisor extends AbstractBehavior<ResumeJobSupervisor.Command> {

    public sealed interface Command permits ProcessJob, JobDone, JobFailed, GetStats {
    }

    public record ProcessJob(JobQueue job) implements Command {
    }

    public record JobDone(UUID jobId) implements Command {
    }

    public record JobFailed(UUID jobId, Throwable error) implements Command {
    }

    public record GetStats(ActorRef<ResumeProcessingStats> replyTo) implements Command {
    }

    private final ResumeProcessingStats stats;
    private final List<ActorRef<ResumeWorkerActor.Command>> workers = new ArrayList<>();
    private int nextWorker = 0;

    private ResumeJobSupervisor(ActorContext<Command> context,
                                ResumeJobProcessor resumeJobProcessor,
                                ResumeProcessingStats stats,
                                int workerCount) {
        super(context);
        this.stats = stats;

        Duration minBackoff = Duration.ofSeconds(1);
        Duration maxBackoff = Duration.ofSeconds(30);
        for (int i = 0; i < workerCount; i++) {
            Behavior<ResumeWorkerActor.Command> worker = Behaviors.supervise(
                    ResumeWorkerActor.create(resumeJobProcessor, stats, getContext().getSelf()))
                    .onFailure(SupervisorStrategy.restartWithBackoff(minBackoff, maxBackoff, 0.2));
            workers.add(getContext().spawn(worker, "resume-worker-" + i));
        }
        getContext().getLog().info("Created {} resume worker actors", workers.size());
    }

    public static Behavior<Command> create(ResumeJobProcessor resumeJobProcessor,
                                           ResumeProcessingStats stats,
                                           int workerCount) {
        return Behaviors.setup(ctx -> new ResumeJobSupervisor(ctx, resumeJobProcessor, stats, workerCount));
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(ProcessJob.class, this::onProcessJob)
                .onMessage(JobDone.class, this::onJobDone)
                .onMessage(JobFailed.class, this::onJobFailed)
                .onMessage(GetStats.class, this::onGetStats)
                .build();
    }

    private Behavior<Command> onProcessJob(ProcessJob message) {
        if (workers.isEmpty()) {
            getContext().getLog().warn("Ignoring job - no workers available: jobId={}", message.job().getId());
            return this;
        }
        ActorRef<ResumeWorkerActor.Command> worker = workers.get(nextWorker);
        nextWorker = (nextWorker + 1) % workers.size();
        stats.onDispatch();
        getContext().getLog().debug("Routing resume job to worker {}: jobId={}, inFlight={}",
                worker.path().name(), message.job().getId(), stats.getInFlight());
        worker.tell(new ResumeWorkerActor.ProcessJob(message.job()));
        return this;
    }

    private Behavior<Command> onJobDone(JobDone message) {
        stats.onCompletion();
        getContext().getLog().debug("Resume job completed: jobId={}, inFlight={}",
                message.jobId(), stats.getInFlight());
        return this;
    }

    private Behavior<Command> onJobFailed(JobFailed message) {
        stats.onCompletion();
        getContext().getLog().error("Resume job failed: jobId={}, error={}",
                message.jobId(), message.error().getMessage(), message.error());
        return this;
    }

    private Behavior<Command> onGetStats(GetStats message) {
        message.replyTo().tell(stats);
        return this;
    }
}
