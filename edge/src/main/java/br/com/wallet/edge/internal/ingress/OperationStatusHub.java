package br.com.wallet.edge.internal.ingress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import br.com.wallet.edge.api.LocalOperationStatusBroadcaster;

/**
 * In-memory status transition broadcaster for SSE streams (REQ-EDG-019).
 * Pushes operation status changes (PROCESSING -> COMPLETED / FAILED) to subscribed frontends.
 */
public class OperationStatusHub implements LocalOperationStatusBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(OperationStatusHub.class);

    private final ConcurrentHashMap<UUID, List<SseEmitter>> emittersByOperation = new ConcurrentHashMap<>();

    public void registerEmitter(UUID operationId, SseEmitter emitter) {
        emittersByOperation.computeIfAbsent(operationId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(operationId, emitter));
        emitter.onTimeout(() -> {
            log.debug("SSE stream timed out for opId: {}", operationId);
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
            removeEmitter(operationId, emitter);
        });
        emitter.onError(e -> {
            log.debug("SSE stream error for opId {}: {}", operationId, e.getMessage());
            removeEmitter(operationId, emitter);
        });
    }

    public void publishStatus(UUID operationId, String status, String message) {
        List<SseEmitter> emitters = emittersByOperation.get(operationId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        OperationStatusResponse response = new OperationStatusResponse(
                operationId,
                status,
                Instant.now(),
                message
        );

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("status")
                        .data(response));

                if (response.isTerminal()) {
                    emitter.complete();
                    removeEmitter(operationId, emitter);
                }
            } catch (IOException e) {
                log.debug("Client disconnected from SSE stream for opId {}: {}", operationId, e.getMessage());
                removeEmitter(operationId, emitter);
            }
        }
    }

    private void removeEmitter(UUID operationId, SseEmitter emitter) {
        List<SseEmitter> list = emittersByOperation.get(operationId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emittersByOperation.remove(operationId);
            }
        }
    }

    public int getActiveSubscriberCount(UUID operationId) {
        List<SseEmitter> list = emittersByOperation.get(operationId);
        return list != null ? list.size() : 0;
    }
}
