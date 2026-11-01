package com.abhishek.resumeanalyzer.pekko;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe counters for the Pekko resume processing pipeline.
 * Shared between the worker pool, the supervisor and the Spring beans that
 * expose queue health/stats.
 */
public class ResumeProcessingStats {

    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicLong processed = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    /**
     * Called by the supervisor when a job is handed to a worker.
     */
    public void onDispatch() {
        inFlight.incrementAndGet();
    }

    /**
     * Called by the supervisor when a worker acknowledges job completion or failure.
     */
    public void onCompletion() {
        inFlight.decrementAndGet();
    }

    /**
     * Called by a worker after a job finished successfully.
     */
    public void recordProcessed() {
        processed.incrementAndGet();
    }

    /**
     * Called by a worker after a job failed.
     */
    public void recordFailed() {
        failed.incrementAndGet();
    }

    public int getInFlight() {
        return inFlight.get();
    }

    public long getProcessedCount() {
        return processed.get();
    }

    public long getFailedCount() {
        return failed.get();
    }
}
