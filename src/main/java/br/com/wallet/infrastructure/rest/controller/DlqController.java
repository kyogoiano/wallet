package br.com.wallet.infrastructure.rest.controller;

import br.com.wallet.dlq.api.DlqManagementUseCase;
import br.com.wallet.dlq.api.DlqQueryUseCase;
import br.com.wallet.dlq.api.dto.DiscardDlqCommand;
import br.com.wallet.dlq.api.dto.DlqOperationResponse;
import br.com.wallet.dlq.api.dto.DlqQueryFilter;
import br.com.wallet.dlq.api.dto.ReplayExhaustedResult;
import br.com.wallet.dlq.api.model.DlqFailureType;
import br.com.wallet.dlq.api.model.DlqStatus;
import br.com.wallet.infrastructure.rest.api.DlqApi;
import jakarta.validation.Valid;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/dlq/operations")
public class DlqController implements DlqApi {

    private static final Logger log = LoggerFactory.getLogger(DlqController.class);

    private final DlqManagementUseCase managementUseCase;
    private final DlqQueryUseCase queryUseCase;

    public DlqController(
            @NonNull final DlqManagementUseCase managementUseCase,
            @NonNull final DlqQueryUseCase queryUseCase
    ) {
        this.managementUseCase = Objects.requireNonNull(managementUseCase, "managementUseCase cannot be null");
        this.queryUseCase = Objects.requireNonNull(queryUseCase, "queryUseCase cannot be null");
    }

    @GetMapping
    @Override
    public ResponseEntity<List<DlqOperationResponse>> listOperations(
            @RequestParam(required = false) final DlqStatus status,
            @RequestParam(required = false) final DlqFailureType failureType,
            @RequestParam(required = false) final String eventType,
            @RequestParam(required = false) final UUID operationId,
            @RequestParam(defaultValue = "50") final Integer limit,
            @RequestParam(defaultValue = "0") final Integer offset
    ) {
        final DlqQueryFilter filter = new DlqQueryFilter(status, failureType, eventType, operationId);
        return ResponseEntity.ok(queryUseCase.findOperations(filter, limit, offset));
    }

    @GetMapping("/{id}")
    @Override
    public ResponseEntity<DlqOperationResponse> getOperation(@PathVariable final UUID id) {
        return queryUseCase.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/replay")
    @Override
    public ResponseEntity<DlqOperationResponse> replayOperation(@PathVariable final UUID id) {
        log.info("Manual replay requested for DLQ operation: {}", id);
        return ResponseEntity.ok(managementUseCase.replayOperation(id));
    }

    @PostMapping("/{id}/discard")
    @Override
    public ResponseEntity<DlqOperationResponse> discardOperation(
            @PathVariable final UUID id,
            @Valid @RequestBody(required = false) final DiscardDlqCommand command
    ) {
        log.info("Manual discard requested for DLQ operation: {}", id);
        final String reason = command != null ? command.reason() : null;
        return ResponseEntity.ok(managementUseCase.discardOperation(id, reason));
    }

    @PostMapping("/replay-exhausted")
    @Override
    public ResponseEntity<ReplayExhaustedResult> replayAllExhausted() {
        log.info("Batch replay requested for all EXHAUSTED DLQ operations");
        return ResponseEntity.ok(managementUseCase.replayAllExhausted());
    }
}
