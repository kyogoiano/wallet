package br.com.wallet.infrasctructure.messaging.consumer;

import br.com.wallet.application.aspects.tracing.TraceContext;
import br.com.wallet.application.usecase.UseCase;
import br.com.wallet.domain.envelope.CommandEnvelope;
import br.com.wallet.exceptions.ExceptionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.*;
import io.nats.client.api.*;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Generalized nats consumer, this will handle nats consumer setup, polling and message processing.
 * Note: Spring will manage all child beans lifecycle.
 * * By default all messages carry on a Trace context, so this will improve traceability and ease generic behaviors
 * @param <T> trace context of the message
 */
public abstract class AbstractNatsConsumer<T extends TraceContext> implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(AbstractNatsConsumer.class);
    static final long maxDeliver = 5; // should match consumer config

    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore semaphore = new Semaphore(50);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Clock clock = Clock.systemUTC();

    private JetStreamSubscription subscription;
    private final String subject;
    private final String dlqSubject;
    private final Connection natsConnection;
    private final ObjectMapper objectMapper;
    private final UseCase<T> useCase;

    public AbstractNatsConsumer(String subject, String dlqSubject, Connection natsConnection, ObjectMapper objectMapper, final UseCase<T> useCase) {
        this.subject = subject;
        this.dlqSubject = dlqSubject;
        this.natsConnection = natsConnection;
        this.objectMapper = objectMapper;
        this.useCase = useCase;
    }

    void setupGeneralSubscription(@NonNull final String streamName,
                                  @NonNull final String consumerName ) throws IOException, JetStreamApiException {
        final var jetStream = natsConnection.jetStream();
        // Configure consumer for at-least-once delivery, backoff follows current retry strategy
        final var consumerConfig = ConsumerConfiguration.builder()
                .durable(consumerName)
                .ackPolicy(AckPolicy.Explicit)
                .maxDeliver(maxDeliver)
                .backoff(backoffSequence)
                .deliverPolicy(DeliverPolicy.New) // 🔥 important in prod
                .build();

        final var pullSubscribeOptions = PullSubscribeOptions.builder()
                .stream(streamName)
                .durable(consumerName)
                .configuration(consumerConfig)
                .build();

        // Subscribe to the subject
        subscription = jetStream.subscribe(subject, pullSubscribeOptions);
        log.info("Subscribed to NATS JetStream subject '{}' with durable consumer '{}'.", subject, consumerName);

    }

    private void pollForMessages() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                // Fetch messages from the stream
                final int available = semaphore.availablePermits();
                final int batchSize = Math.min(available, 10);

                if (batchSize == 0) {
                    Thread.sleep(10); // small backoff
                    continue;
                }

                final Duration timeout = Duration.ofMillis(500);

                final var messages = subscription.fetch(batchSize, timeout);

                if (messages.isEmpty()) {
                    continue; // keep polling
                }
                log.debug("Fetched {} messages from NATS JetStream.", messages.size());

                // bulkhead pattern
                for (final Message msg : messages) {
                    semaphore.acquire();

                    executorService.submit(() -> {
                        try {
                            processMessage(msg);
                        } finally {
                            semaphore.release();
                        }
                    });
                }
            } catch (Exception e) {
                log.error("Error fetching messages from NATS JetStream: {}", e.getMessage(), e);
                try {
                    Thread.sleep(1000); // Wait before retrying fetch
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public abstract void init() throws Exception;

    protected void beforeHandle(CommandEnvelope<T> envelope, Message message) {}

    protected void afterHandle(CommandEnvelope<T> envelope, Message message) {}

    protected void onFailure(Exception e, CommandEnvelope<T> envelope, Message message) {}

    void processMessage(@NonNull final Message message) {
        final CommandEnvelope<T> envelope;
        try {
            envelope = objectMapper.readValue(
                    message.getData(),
                    objectMapper.getTypeFactory()
                            .constructParametricType(CommandEnvelope.class, Class.forName(message.getHeaders().getFirst("type")))
            );
        } catch (Exception e) {
            log.error("Invalid payload → DLQ");
            handleDlqMessage(dlqSubject, natsConnection, message, e);
            message.ack();
            return;
        }

        final long deliveries = message.metaData().deliveredCount();
        final var operationId = envelope.operationId();

        try {
            log.info("Processing attempt {} for operationId={}", deliveries, operationId);

            // 💼 Transactional business logic
            useCase.handle(envelope.payload());
            message.ack();

            log.info("event=processed operationId={} subject={} deliveries={}",
                    operationId, subject, deliveries);

        } catch (Exception e) {
            if(RetryPolicy.decide(deliveries, e).equals(RetryDecision.DLQ)) {
                log.error("Max delivery reached for operationId={}, sending to DLQ", operationId);
                handleDlqMessage(dlqSubject, natsConnection, message, e);
                message.ack();
                return;
            }
            // 🔁 retry via JetStream
            log.warn("Transient failure, will retry: {}", e.getMessage());
            message.nakWithDelay(retryDelay(deliveries));

        }
    }

    void handleDlqMessage(@NonNull String subject, @NonNull Connection connection, @NonNull Message message, @NonNull Exception error) {
        log.error("Poison message detected: subject={}", message.getSubject());
        final var now = clock.instant();
        final var newHeaders = new Headers();

        newHeaders.add("original_subject", message.getSubject());
        newHeaders.add("operation_id", message.getHeaders().getFirst("operation_id"));
        newHeaders.add("type", message.getHeaders().getFirst("type"));
        newHeaders.add("failed_at", now.toString());
        newHeaders.add("error", error.getClass().getSimpleName());
        newHeaders.add("error_message", error.getMessage());
        newHeaders.add("delivery_count", String.valueOf(message.metaData().deliveredCount()));
        newHeaders.add("failure_type", ExceptionType.parseException(error).name());
        newHeaders.add("Nats-Msg-Id", message.getHeaders().getFirst("Nats-Msg-Id"));

        final var dlqMessage = NatsMessage.builder()
                .subject(subject)
                .headers(newHeaders)
                .data(message.getData())
                .build();

        try {
            final JetStream jetStream = connection.jetStream();
            jetStream.publish(dlqMessage);
        } catch (Exception ex) {
            log.error("Failed to publish to DLQ", ex);
        }

    }

    void replay(@NonNull final Message dlqMessage, @NonNull final Connection connection) {

        dlqMessage.getHeaders().add("replayed", "true");
        dlqMessage.getHeaders().add("replay_at", clock.instant().toString());
        final long deliveries = dlqMessage.metaData().deliveredCount();

        final var originalSubject = dlqMessage.getHeaders().getFirst("original_subject");

        final var replayMessage = NatsMessage.builder()
                .subject(originalSubject)
                .headers(dlqMessage.getHeaders())
                .data(dlqMessage.getData())
                .build();

        try {
            final JetStream jetStream = connection.jetStream();
            jetStream.publish(replayMessage);
        } catch (Exception ex) {
            log.error("Failed to publish to DLQ", ex);
            dlqMessage.nakWithDelay(retryDelay(deliveries));
        }
    }


    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            try {
                init(); // 🔥 ensure subscription exists before polling
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize consumer", e);
            }
            executorService.submit(this::pollForMessages);
        }
    }

    @Override
    public void stop() {
        running.set(false);

        if (subscription != null) {
            subscription.unsubscribe();
            log.info("Unsubscribed from NATS JetStream.");
        }

        executorService.shutdown(); // immediate stop due to virtual threads
        log.info("NATS message consumer executor service shut down.");
        try {
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE; // start late, stop early
    }

    private Duration retryDelay(long deliveries) {
        return switch ((int) deliveries) {
            case 1 -> Duration.ofSeconds(1);
            case 2 -> Duration.ofSeconds(5);
            case 3 -> Duration.ofSeconds(10);
            default -> Duration.ofSeconds(30);
        };
    }

    private static final Duration[] backoffSequence = new Duration[] {
            Duration.ofSeconds(1),
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            Duration.ofSeconds(30)
    };
}
