package br.com.wallet.dlq.internal.service;

import br.com.wallet.core.tracing.Traceable;
import br.com.wallet.dlq.api.DlqManagementUseCase;
import br.com.wallet.dlq.api.dto.DlqOperationResponse;
import br.com.wallet.dlq.api.dto.ReplayExhaustedResult;
import br.com.wallet.dlq.api.model.DlqEvent;
import br.com.wallet.dlq.api.model.DlqStatus;
import br.com.wallet.dlq.internal.persistence.DlqOperationsDao;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
public class DlqManagementService implements DlqManagementUseCase {

    private static final Logger log = LoggerFactory.getLogger(DlqManagementService.class);

    private final DlqOperationsDao dlqDao;
    private final Connection connection;
    private final Clock clock;

    public DlqManagementService(
            @NonNull final DlqOperationsDao dlqDao,
            @NonNull final Connection connection,
            @NonNull final Clock clock
    ) {
        this.dlqDao = Objects.requireNonNull(dlqDao, "dlqDao cannot be null");
        this.connection = Objects.requireNonNull(connection, "connection cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    @Traceable("dlq.record_event")
    public void recordDlqEvent(@NonNull final DlqEvent dlqEvent) {
        Objects.requireNonNull(dlqEvent, "dlqEvent cannot be null");
        dlqDao.insert(dlqEvent);
    }

    @Override
    @NonNull
    @Traceable("dlq.manual_replay")
    public DlqOperationResponse replayOperation(@NonNull final UUID dlqEventId) {
        Objects.requireNonNull(dlqEventId, "dlqEventId cannot be null");

        final DlqEvent event = dlqDao.findById(dlqEventId)
                .orElseThrow(() -> new NoSuchElementException("DLQ operation not found: " + dlqEventId));

        if (event.status() == DlqStatus.COMPLETED) {
            throw new IllegalArgumentException("Cannot replay already COMPLETED DLQ operation: " + dlqEventId);
        }

        final Instant now = clock.instant();
        publishToNats(event);
        dlqDao.markAsCompleted(dlqEventId, now);

        log.info("Manually replayed DLQ operation {} to subject {}", dlqEventId, event.subject());
        return dlqDao.findById(dlqEventId).map(DlqOperationResponse::from).orElseThrow();
    }

    @Override
    @NonNull
    @Traceable("dlq.manual_discard")
    public DlqOperationResponse discardOperation(@NonNull final UUID dlqEventId, @Nullable final String reason) {
        Objects.requireNonNull(dlqEventId, "dlqEventId cannot be null");

        final DlqEvent event = dlqDao.findById(dlqEventId)
                .orElseThrow(() -> new NoSuchElementException("DLQ operation not found: " + dlqEventId));

        if (event.status() == DlqStatus.COMPLETED) {
            throw new IllegalArgumentException("Cannot discard already COMPLETED DLQ operation: " + dlqEventId);
        }

        final Instant now = clock.instant();
        dlqDao.markAsDiscarded(dlqEventId, now, reason != null ? reason : "Manually discarded by operator");

        log.info("Manually discarded DLQ operation {}. reason={}", dlqEventId, reason);
        return dlqDao.findById(dlqEventId).map(DlqOperationResponse::from).orElseThrow();
    }

    @Override
    @NonNull
    @Traceable("dlq.batch_replay_exhausted")
    public ReplayExhaustedResult replayAllExhausted() {
        final List<DlqEvent> exhaustedEvents = dlqDao.findExhaustedOperations(100);
        final List<UUID> replayedIds = new ArrayList<>();
        final Instant now = clock.instant();

        for (final DlqEvent event : exhaustedEvents) {
            try {
                publishToNats(event);
                dlqDao.markAsCompleted(event.id(), now);
                replayedIds.add(event.id());
            } catch (Exception e) {
                log.error("Failed to replay exhausted DLQ operation {}", event.id(), e);
            }
        }

        log.info("Batch replay executed for {} exhausted operations", replayedIds.size());
        return new ReplayExhaustedResult(replayedIds.size(), replayedIds);
    }

    private void publishToNats(@NonNull final DlqEvent event) {
        try {
            final var headers = new Headers();
            headers.add("operation_id", event.operationId().toString());
            if (event.userId() != null) {
                headers.add("userId", event.userId().toString());
            }
            headers.add("replayed", "true");
            headers.add("replay_count", String.valueOf(event.retryCount()));
            headers.add("type", event.eventType());

            final var message = NatsMessage.builder()
                    .subject(event.subject())
                    .headers(headers)
                    .data(event.payload().getBytes())
                    .build();

            final JetStream jetStream = connection.jetStream();
            jetStream.publish(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish replayed message to NATS: " + e.getMessage(), e);
        }
    }
}
