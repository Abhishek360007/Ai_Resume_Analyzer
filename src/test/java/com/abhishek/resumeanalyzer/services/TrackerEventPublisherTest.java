package com.abhishek.resumeanalyzer.services;

import com.abhishek.resumeanalyzer.entities.ProcessTracker;
import com.abhishek.resumeanalyzer.models.ProcessStatus;
import com.abhishek.resumeanalyzer.models.TrackerStatusEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link TrackerEventPublisher}.
 *
 * <p>Mocks the {@link SseEmitter} so the broadcast logic (send, dead-emitter
 * cleanup) can be verified without an HTTP container.</p>
 */
@ExtendWith(MockitoExtension.class)
class TrackerEventPublisherTest {

    @Mock
    private SseEmitter mockEmitter;

    private TrackerEventPublisher publisher;
    private ProcessTracker tracker;

    @BeforeEach
    void setUp() {
        publisher = new TrackerEventPublisher();
        tracker = ProcessTracker.builder()
                .id(UUID.randomUUID())
                .status(ProcessStatus.RESUME_ANALYZED)
                .totalFiles(3)
                .processedFiles(1)
                .failedFiles(0)
                .message("Resume analyzed")
                .uploadedFilename("3 files")
                .build();
    }

    @Test
    @DisplayName("Subscribe registers a new client emitter")
    void subscribeRegistersEmitter() {
        SseEmitter emitter = publisher.subscribe();
        assertThat(emitter).isNotNull();
        assertThat(publisher.getEmitterCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Publish broadcasts the event to connected clients")
    void publishSendsEventToConnectedClients() throws IOException {
        publisher.register(mockEmitter);

        assertThatCode(() -> publisher.publish(tracker, TrackerStatusEvent.TYPE_UPDATE))
                .doesNotThrowAnyException();

        verify(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("Publish drops dead emitters without failing the broadcast")
    void publishRemovesDeadEmittersAndContinues() throws IOException {
        publisher.register(mockEmitter);
        doThrow(new IOException("client gone")).when(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));

        assertThatCode(() -> publisher.publish(tracker, TrackerStatusEvent.TYPE_PROCESSED))
                .doesNotThrowAnyException();

        assertThat(publisher.getEmitterCount()).isZero();
    }
}
