package br.com.wallet.infrasctructure.messaging.consumer;

import br.com.wallet.application.usecase.TransferFundsUseCase;
import br.com.wallet.domain.context.Transfer;
import br.com.wallet.domain.envelope.CommandEnvelope;
import br.com.wallet.exceptions.BusinessException;
import br.com.wallet.exceptions.TransientException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.*;
import jakarta.annotation.PostConstruct;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

/**
 * One consumer for each use case. Or we should create a generica consumer ?
 */
//@DependsOn("jetStreamInitializer") // it enforces stream creation before this consumer is initialized
@Component
public class TransferCommandConsumer extends AbstractNatsConsumer {
    private static final String subject = "commands.transfer"; // Subject for transfer commands
    private static final String durableConsumerName = "transfer-consumer"; // Durable consumer name for JetStream
    private static final Logger log = LoggerFactory.getLogger(TransferCommandConsumer.class);
    private final Connection natsConnection;
    private final ObjectMapper objectMapper;
    private final TransferFundsUseCase transferUseCase; // Your business logic
    private final IdempotencyService idempotencyService;

    public TransferCommandConsumer(final Connection natsConnection,
                                   final ObjectMapper objectMapper,
                                   final TransferFundsUseCase transferUseCase,
                                   final IdempotencyService idempotencyService) {
        this.natsConnection = natsConnection;
        this.objectMapper = objectMapper;
        this.transferUseCase = transferUseCase;
        this.idempotencyService = idempotencyService;
    }

    @PostConstruct
    public void setupSubscription() throws IOException, JetStreamApiException {
        setupGeneralSubscription(subject, durableConsumerName, natsConnection);
    }

    @Override
    void processMessage(@NonNull final Message message) {
        try {
            var envelope = objectMapper.readValue(
                    message.getData(),
                    new TypeReference<CommandEnvelope<Transfer>>() {}
            );

            UUID operationId = envelope.operationId();

            // 🧠 Idempotency check
            if (idempotencyService.isProcessed(operationId)) {
                log.info("Skipping already processed operationId={}", operationId);
                message.ack();
                return;
            }

            final long deliveries = message.metaData().deliveredCount();
            log.warn("Processing attempt {} for operationId={}", deliveries, operationId);

            // 💼 Execute business logic (transactional) -> TODO: also the use case will responsible to include idempotency wallet operation repo through an annotation that will trigger this inclusion
            transferUseCase.handle(envelope.payload());
            message.ack();

            log.info("Processed operationId={}", operationId);

        } catch (BusinessException e) {
            // ❗ DO NOT retry
            log.warn("Business failure: {}", e.getMessage());
            message.ack();

        } catch (TransientException e) {
            // 🔁 retry via JetStream
            log.warn("Transient failure, will retry: {}", e.getMessage());
            message.nakWithDelay(Duration.ofSeconds(1));

        } catch (Exception e) {
            // 💥 unknown = retry (safe fallback)
            final var headers = message.getHeaders();
            final var opId = headers != null ? headers.getFirst("operation_id") : "unknown";
            log.error("Failed message, operationId={}, subject={}",
                    opId,
                    message.getSubject()
            );
            message.nakWithDelay(Duration.ofSeconds(1));
        }
    }
}