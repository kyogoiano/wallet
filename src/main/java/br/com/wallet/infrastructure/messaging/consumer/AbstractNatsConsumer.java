package br.com.wallet.infrastructure.messaging.consumer;

import io.nats.client.*;
import io.nats.client.api.*;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Generalized nats consumer, this will handle nats consumer setup and polling.
 * Note: Spring will manage all child beans lifecycle.
 */
public abstract class AbstractNatsConsumer implements SmartLifecycle {
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    static final long maxDeliver = 5; // should match consumer config

    private ExecutorService executorService;
    private final Semaphore semaphore = new Semaphore(50);
    private final AtomicBoolean running = new AtomicBoolean(false);

    private JetStreamSubscription subscription;
    final String subject;
    final Connection natsConnection;

    public AbstractNatsConsumer(final String subject,
                                final Connection natsConnection) {
        this.subject = subject;
        this.natsConnection = natsConnection;
    }

    protected void setupGeneralSubscription(@NonNull final String streamName,
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
                    try {
                        executorService.submit(() -> {
                            try {
                                this.processMessage(msg);
                            } finally {
                                semaphore.release();
                            }
                        });
                    } catch (java.util.concurrent.RejectedExecutionException e) {
                        semaphore.release();
                        if (running.get()) {
                            throw e;
                        }
                        break;
                    }
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

    abstract void processMessage(@NonNull Message message);

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            try {
                init(); // 🔥 ensure subscription exists before polling
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize consumer", e);
            }
            executorService = Executors.newVirtualThreadPerTaskExecutor();
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

        if (executorService != null) {
            executorService.shutdown(); // immediate stop due to virtual threads
            log.info("NATS message consumer executor service shut down.");
            try {
                executorService.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
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

    Duration retryDelay(long deliveries) {
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
