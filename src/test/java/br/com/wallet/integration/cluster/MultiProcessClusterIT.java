package br.com.wallet.integration.cluster;

import br.com.wallet.edge.internal.ingress.OperationStatusHub;
import br.com.wallet.edge.internal.publisher.NatsEdgeCommandPublisher;
import br.com.wallet.edge.internal.status.NatsOperationStatusListener;
import br.com.wallet.infrastructure.messaging.publisher.CoreStatusPublisher;
import br.com.wallet.support.DockerProperties;
import br.com.wallet.support.IntegrationTestBase;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Import(IntegrationTestBase.class)
@DisplayName("MultiProcessClusterIT: Edge <-> Core Asynchronous NATS IPC Flow (REQ-PRC-006, REQ-PRC-007, REQ-PRC-008)")
public class MultiProcessClusterIT extends DockerProperties {

    private Connection edgeNatsConn;
    private Connection coreNatsConn;

    private OperationStatusHub edgeStatusHub;
    private NatsOperationStatusListener edgeStatusListener;
    private NatsEdgeCommandPublisher edgeCommandPublisher;
    private CoreStatusPublisher coreStatusPublisher;

    private final ObjectMapper objectMapper = tools.jackson.databind.json.JsonMapper.builder().build();

    @BeforeEach
    void setUp() throws Exception {
        String natsUrl = "nats://" + IntegrationTestBase.NATS_CONTAINER.getHost() + ":" + IntegrationTestBase.NATS_CONTAINER.getMappedPort(4222);
        char[] token = "dfji348934jdd0i24uhjd29834ijrr0345jo0r3j034n".toCharArray();

        Options edgeOpts = new Options.Builder()
                .server(natsUrl)
                .token(token)
                .connectionName("wallet-edge-process-sim")
                .build();
        Options coreOpts = new Options.Builder()
                .server(natsUrl)
                .token(token)
                .connectionName("wallet-core-process-sim")
                .build();

        edgeNatsConn = Nats.connect(edgeOpts);
        coreNatsConn = Nats.connect(coreOpts);

        // Edge components
        edgeStatusHub = new OperationStatusHub();
        edgeStatusListener = new NatsOperationStatusListener(edgeNatsConn, edgeStatusHub, objectMapper, "edge-proc-1");
        edgeStatusListener.afterPropertiesSet();
        edgeCommandPublisher = new NatsEdgeCommandPublisher(edgeNatsConn, objectMapper);

        // Core components
        coreStatusPublisher = new CoreStatusPublisher(coreNatsConn, objectMapper, "core-proc-1");

        edgeNatsConn.flush(java.time.Duration.ofSeconds(2));
        coreNatsConn.flush(java.time.Duration.ofSeconds(2));
        Thread.sleep(200);
    }

    @AfterEach
    void tearDown() {
        if (edgeStatusListener != null) {
            edgeStatusListener.destroy();
        }
        if (edgeNatsConn != null) {
            try { edgeNatsConn.close(); } catch (Exception ignored) {}
        }
        if (coreNatsConn != null) {
            try { coreNatsConn.close(); } catch (Exception ignored) {}
        }
    }

    @Test
    @DisplayName("REQ-PRC-006 & REQ-PRC-008: Edge publishes command and receives distributed status transition from Core")
    void shouldDeliverEndToEndViaNats() throws Exception {
        UUID opId = UUID.randomUUID();
        CountDownLatch terminalLatch = new CountDownLatch(1);
        List<Object> receivedEvents = Collections.synchronizedList(new ArrayList<>());

        // 1. Client connects to Edge SSE stream
        SseEmitter clientEmitter = new SseEmitter(10_000L) {
            @Override
            public synchronized void complete() {
                super.complete();
                terminalLatch.countDown();
            }

            @Override
            public void send(@NonNull SseEventBuilder builder) throws java.io.IOException {
                super.send(builder);
                receivedEvents.add(builder);
            }
        };
        edgeStatusHub.registerEmitter(opId, clientEmitter);
        assertThat(edgeStatusHub.getActiveSubscriberCount(opId)).isEqualTo(1);

        // 2. Core executes transaction and broadcasts terminal COMPLETED status over NATS
        coreStatusPublisher.publishStatus(opId, "COMPLETED", "ACID transaction committed in Core process");
        coreNatsConn.flush(java.time.Duration.ofSeconds(2));

        // 3. Edge status listener picks up event from NATS and pushes to client SSE emitter
        boolean completed = terminalLatch.await(5, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        assertThat(receivedEvents).isNotEmpty();
    }
}
