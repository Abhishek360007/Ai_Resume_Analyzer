package com.abhishek.resumeanalyzer.pekko.actors;

import com.abhishek.resumeanalyzer.entities.JobQueue;
import com.abhishek.resumeanalyzer.pekko.ResumeProcessingStats;
import com.abhishek.resumeanalyzer.services.ResumeJobProcessor;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;

import java.util.UUID;

/**
 * Worker actor that executes the resume processing pipeline for a single job.
 * An actor is single-threaded, so each worker handles one job at a time; the
 * pool size therefore bounds the concurrency of the pipeline.
 *
 * <p>On completion (or failure) the worker acknowledges the result to the
 * {@link ResumeJobSupervisor} so it can track in-flight work.
 */
public class ResumeWorkerActor extends AbstractBehavior<ResumeWorkerActor.Command> {

    public sealed interface Command permits ProcessJob {
    }

    public record ProcessJob(JobQueue job) implements Command {
    }

    private final ResumeJobProcessor resumeJobProcessor;
    private final ResumeProcessingStats stats;
    private final ActorRef<ResumeJobSupervisor.Command> supervisor;

    private ResumeWorkerActor(ActorContext<Command> context,
                              ResumeJobProcessor resumeJobProcessor,
                              ResumeProcessingStats stats,
                              ActorRef<ResumeJobSupervisor.Command> supervisor) {
        super(context);
        this.resumeJobProcessor = resumeJobProcessor;
        this.stats = stats;
        this.supervisor = supervisor;
    }

    public static Behavior<Command> create(ResumeJobProcessor resumeJobProcessor,
                                           ResumeProcessingStats stats,
                                           ActorRef<ResumeJobSupervisor.Command> supervisor) {
        return Behaviors.setup(ctx -> new ResumeWorkerActor(ctx, resumeJobProcessor, stats, supervisor));
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(ProcessJob.class, this::onProcessJob)
                .build();
    }

    private Behavior<Command> onProcessJob(ProcessJob message) {
        UUID jobId = message.job().getId();
        getContext().getLog().info("Worker {} processing resume job: jobId={}",
                getContext().getSelf().path().name(), jobId);
        try {
            resumeJobProcessor.processJob(message.job());
            stats.recordProcessed();
            getContext().getLog().info("Worker {} finished resume job: jobId={}",
                    getContext().getSelf().path().name(), jobId);
            supervisor.tell(new ResumeJobSupervisor.JobDone(jobId));
        } catch (Exception e) {
            stats.recordFailed();
            getContext().getLog().error("Worker {} failed resume job: jobId={}, error={}",
                    getContext().getSelf().path().name(), jobId, e.getMessage(), e);
            supervisor.tell(new ResumeJobSupervisor.JobFailed(jobId, e));
        }
        return this;
    }
}
