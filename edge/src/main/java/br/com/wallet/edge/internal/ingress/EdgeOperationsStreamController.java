package br.com.wallet.edge.internal.ingress;

import br.com.wallet.edge.api.DurableOperationStateProvider;
import br.com.wallet.edge.api.DurableOperationStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Reactive Server-Sent Events controller pushing real-time operation status to frontends (REQ-EDG-019).
 * Eliminates HTTP polling over mobile and web connections.
 * Bootstraps from durable state provider before attaching live listener (I-EDGE-007).
 */
@RestController
@RequestMapping("/operations")
public class EdgeOperationsStreamController {

    private final OperationStatusHub statusHub;
    private final DurableOperationStateProvider durableStateProvider;

    public EdgeOperationsStreamController(OperationStatusHub statusHub, DurableOperationStateProvider durableStateProvider) {
        this.statusHub = statusHub;
        this.durableStateProvider = durableStateProvider;
    }

    @GetMapping(value = "/{operationId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamOperationStatus(@PathVariable("operationId") UUID operationId) {
        // I-EDGE-007: Check durable state first before attaching to live hub
        Optional<DurableOperationStatus> durable = durableStateProvider.findOperationStatus(operationId);
        if (durable.isPresent() && durable.get().isTerminal()) {
            SseEmitter emitter = new SseEmitter(60_000L);
            try {
                emitter.send(SseEmitter.event()
                        .name("status")
                        .data(new OperationStatusResponse(
                                operationId,
                                durable.get().status(),
                                durable.get().timestamp(),
                                durable.get().message()
                        )));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        // 60-second timeout for financial status resolution
        SseEmitter emitter = new SseEmitter(60_000L);
        statusHub.registerEmitter(operationId, emitter);

        // Send immediate initial status event
        try {
            String initialStatus = durable.map(DurableOperationStatus::status).orElse(OperationStatusResponse.STATUS_PROCESSING);
            String initialMsg = durable.map(DurableOperationStatus::message).orElse("Operation accepted and queued for settlement");
            Instant initialTime = durable.map(DurableOperationStatus::timestamp).orElseGet(Instant::now);

            emitter.send(SseEmitter.event()
                    .name("status")
                    .data(new OperationStatusResponse(
                            operationId,
                            initialStatus,
                            initialTime,
                            initialMsg
                    )));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }
}
