package com.abhishek.resumeanalyzer.services;

import com.abhishek.resumeanalyzer.entities.ProcessTracker;
import com.abhishek.resumeanalyzer.models.TrackerStatusEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Broadcasts {@link ProcessTracker} updates to all connected SSE clients.
 *
 * <p>The resume processing pipeline runs in the background (Apache Pekko actors
 * consuming the job queue) regardless of whether any browser is watching.  This
 * publisher is the push channel that mirrors those durable status changes to
 * live clients, with a snapshot replayed on every new connection so a client
 * that navigated away, refreshed, or reconnected never loses the current state.</p>
 */
@Service
@Slf4j
public class TrackerEventPublisher {

    private static final long HEARTBEAT_INTERVAL_SECONDS = 15L;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService heartbeatExecutor =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "tracker-event-heartbeat");
                thread.setDaemon(true);
                return thread;
            });

    public TrackerEventPublisher() {
        heartbeatExecutor.scheduleAtFixedRate(
                this::sendHeartbeat, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Register a new client connection and return the emitter to stream to.
     * The emitter is auto-removed when the client disconnects, times out, or errors.
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        register(emitter);
        return emitter;
    }

    /**
     * Broadcast an update for a process tracker to every connected client.
     */
    public void publish(ProcessTracker tracker, String type) {
        publish(TrackerStatusEvent.from(tracker, type), type);
    }

    /**
     * Broadcast a pre-built event to every connected client.
     */
    public void publish(TrackerStatusEvent event, String type) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(type).data(event));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
            }
        }
    }

    /**
     * Number of currently connected clients (for monitoring / tests).
     */
    public int getEmitterCount() {
        return emitters.size();
    }

    /**
     * Package-visible for tests: attach an existing emitter to the broadcast list.
     */
    void register(SseEmitter emitter) {
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
    }

    private void sendHeartbeat() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("keep-alive"));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
            }
        }
    }
}
