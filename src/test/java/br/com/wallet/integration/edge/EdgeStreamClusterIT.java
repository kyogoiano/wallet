package br.com.wallet.integration.edge;

import br.com.wallet.edge.internal.ingress.OperationStatusHub;
import br.com.wallet.infrastructure.messaging.status.NatsOperationStatusBroadcaster;
import br.com.wallet.infrastructure.messaging.status.NatsOperationStatusListener;
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
@DisplayName("EdgeStreamClusterIT: Multi-Instance SSE Status Fan-Out Integration Test (TASK-5.8, TASK-5.9, REQ-EDG-019)")
public class EdgeStreamClusterIT extends DockerProperties {

    private Connection natsConn1;
    private Connection natsConn2;

    private OperationStatusHub hubNode1;
    private OperationStatusHub hubNode2;

    private NatsOperationStatusBroadcaster broadcasterNode1;
    private NatsOperationStatusListener listenerNode2;
    private NatsOperationStatusListener listenerNode1;

    private final ObjectMapper objectMapper = tools.jackson.databind.json.JsonMapper.builder().build();

    @BeforeEach
    void setUp() throws Exception {
        String natsUrl = "nats://" + IntegrationTestBase.NATS_CONTAINER.getHost() + ":" + IntegrationTestBase.NATS_CONTAINER.getMappedPort(4222);
        char[] token = "dfji348934jdd0i24uhjd29834ijrr0345jo0r3j034n".toCharArray();

        Options options1 = new Options.Builder()
                .server(natsUrl)
                .token(token)
                .connectionName("test-edge-node-1")
                .build();
        Options options2 = new Options.Builder()
                .server(natsUrl)
                .token(token)
                .connectionName("test-edge-node-2")
                .build();

        natsConn1 = Nats.connect(options1);
        natsConn2 = Nats.connect(options2);

        hubNode1 = new OperationStatusHub();
        hubNode2 = new OperationStatusHub();

        broadcasterNode1 = new NatsOperationStatusBroadcaster(natsConn1, hubNode1, objectMapper, "node-1");
        listenerNode1 = new NatsOperationStatusListener(natsConn1, hubNode1, objectMapper, "node-1");
        listenerNode2 = new NatsOperationStatusListener(natsConn2, hubNode2, objectMapper, "node-2");

        listenerNode1.afterPropertiesSet();
        listenerNode2.afterPropertiesSet();

        // Flush subscriptions to NATS server
        natsConn1.flush(java.time.Duration.ofSeconds(2));
        natsConn2.flush(java.time.Duration.ofSeconds(2));

        // Allow NATS subscription propagation
        Thread.sleep(200);
    }

    @AfterEach
    void tearDown() {
        if (listenerNode1 != null) listenerNode1.destroy();
        if (listenerNode2 != null) listenerNode2.destroy();
        if (natsConn1 != null) {
            try { natsConn1.close(); } catch (Exception ignored) {}
        }
        if (natsConn2 != null) {
            try { natsConn2.close(); } catch (Exception ignored) {}
        }
    }

    @Test
    @DisplayName("Should deliver status generated on Node 1 to SSE subscriber connected to Node 2 (TASK-5.8, TASK-5.9)")
    void shouldDeliverStatusAcrossEdgeNodes() throws Exception {
        UUID opId = UUID.randomUUID();
        CountDownLatch latch = new CountDownLatch(1);
        List<Object> receivedEvents = Collections.synchronizedList(new ArrayList<>());

        // Subscriber connects to Node 2 with overridden complete() to observe terminal completion
        SseEmitter emitterOnNode2 = new SseEmitter(10_000L) {
            @Override
            public synchronized void complete() {
                super.complete();
                latch.countDown();
            }

            @Override
            public void send(@NonNull SseEventBuilder builder) throws java.io.IOException {
                super.send(builder);
                receivedEvents.add(builder);
            }
        };
        hubNode2.registerEmitter(opId, emitterOnNode2);
        assertThat(hubNode2.getActiveSubscriberCount(opId)).isEqualTo(1);

        // Node 1 broadcasts terminal COMPLETED status
        broadcasterNode1.publishStatus(opId, "COMPLETED", "Settled across cluster on Node 1");
        natsConn1.flush(java.time.Duration.ofSeconds(2));

        // Await delivery on Node 2
        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertThat(completed).isTrue();

        // Verify emitter on Node 2 was completed and deregistered after receiving terminal event
        assertThat(hubNode2.getActiveSubscriberCount(opId)).isEqualTo(0);
        assertThat(receivedEvents).hasSize(1);
    }
}
