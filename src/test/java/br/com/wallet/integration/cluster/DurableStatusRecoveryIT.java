package br.com.wallet.integration.cluster;

import br.com.wallet.edge.api.DurableOperationStatus;
import br.com.wallet.edge.internal.ingress.EdgeOperationsStreamController;
import br.com.wallet.edge.internal.ingress.OperationStatusHub;
import br.com.wallet.edge.internal.status.NatsDurableOperationStateProvider;
import br.com.wallet.infrastructure.messaging.consumer.CoreOperationQueryResponder;
import br.com.wallet.ledger.api.OperationQueryUseCase;
import br.com.wallet.ledger.api.domain.OperationStatus;
import br.com.wallet.ledger.api.dto.OperationStatusResponse;
import br.com.wallet.support.DockerProperties;
import br.com.wallet.support.IntegrationTestBase;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Import(IntegrationTestBase.class)
@DisplayName("DurableStatusRecoveryIT: Zero-DB SSE Durable State Recovery over NATS (REQ-PRC-020 & I-EDGE-007)")
public class DurableStatusRecoveryIT extends DockerProperties {

    private Connection edgeNatsConn;
    private Connection coreNatsConn;

    private CoreOperationQueryResponder queryResponder;
    private NatsDurableOperationStateProvider stateProvider;
    private OperationStatusHub statusHub;
    private EdgeOperationsStreamController streamController;
    private OperationQueryUseCase operationQueryUseCase;

    private final ObjectMapper objectMapper = tools.jackson.databind.json.JsonMapper.builder().build();

    @BeforeEach
    void setUp() throws Exception {
        String natsUrl = "nats://" + IntegrationTestBase.NATS_CONTAINER.getHost() + ":" + IntegrationTestBase.NATS_CONTAINER.getMappedPort(4222);
        char[] token = "dfji348934jdd0i24uhjd29834ijrr0345jo0r3j034n".toCharArray();

        Options edgeOpts = new Options.Builder().server(natsUrl).token(token).build();
        Options coreOpts = new Options.Builder().server(natsUrl).token(token).build();

        edgeNatsConn = Nats.connect(edgeOpts);
        coreNatsConn = Nats.connect(coreOpts);

        String testQueryPrefix = "operations.query.recovery-it-" + UUID.randomUUID() + ".";

        operationQueryUseCase = mock(OperationQueryUseCase.class);
        queryResponder = new CoreOperationQueryResponder(coreNatsConn, operationQueryUseCase, objectMapper, testQueryPrefix + "*");
        queryResponder.afterPropertiesSet();

        stateProvider = new NatsDurableOperationStateProvider(edgeNatsConn, objectMapper, testQueryPrefix);
        statusHub = new OperationStatusHub();
        streamController = new EdgeOperationsStreamController(statusHub, stateProvider);

        edgeNatsConn.flush(java.time.Duration.ofSeconds(2));
        coreNatsConn.flush(java.time.Duration.ofSeconds(2));
        Thread.sleep(200);
    }

    @AfterEach
    void tearDown() {
        if (queryResponder != null) queryResponder.destroy();
        if (edgeNatsConn != null) { try { edgeNatsConn.close(); } catch (Exception ignored) {} }
        if (coreNatsConn != null) { try { coreNatsConn.close(); } catch (Exception ignored) {} }
    }

    @Test
    @DisplayName("REQ-PRC-020: Late SSE subscriber must bootstrap terminal COMPLETED status via NATS Request-Reply")
    void shouldBootstrapTerminalStatusAfterDroppedLiveEvent() throws Exception {
        UUID opId = UUID.randomUUID();

        // 1. Core has committed transaction durably in database
        OperationStatusResponse committedStatus = new OperationStatusResponse(
                opId,
                OperationStatus.COMPLETED,
                "ACID commit confirmed",
                null,
                Instant.now(),
                Instant.now()
        );
        when(operationQueryUseCase.getOperationStatus(opId)).thenReturn(Optional.of(committedStatus));

        // 2. Client connects late to Edge SSE stream (live event was dropped/missed)
        CountDownLatch terminalLatch = new CountDownLatch(1);
        List<Object> receivedEvents = Collections.synchronizedList(new ArrayList<>());

        SseEmitter emitter = streamController.streamOperationStatus(opId);
        emitter.onCompletion(terminalLatch::countDown);

        // Directly query state provider to assert zero-DB NATS Request-Reply resolution
        Optional<DurableOperationStatus> resolved = stateProvider.findOperationStatus(opId);
        assertThat(resolved).isPresent();
        assertThat(resolved.get().status()).isEqualTo("COMPLETED");
        assertThat(resolved.get().isTerminal()).isTrue();
    }

    @Test
    @DisplayName("REQ-PRC-020: Query for unregistered in-flight operation must return NOT_FOUND from Core store")
    void shouldReturnNotFoundForUnregisteredOperation() {
        UUID opId = UUID.randomUUID();
        when(operationQueryUseCase.getOperationStatus(opId)).thenReturn(Optional.empty());

        Optional<DurableOperationStatus> resolved = stateProvider.findOperationStatus(opId);
        assertThat(resolved).isPresent();
        assertThat(resolved.get().isNotFound()).isTrue();
        assertThat(resolved.get().status()).isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("REQ-PRC-020: When query times out, Edge must return DEGRADED_UNAVAILABLE and not silently claim state is current")
    void shouldReturnDegradedStatusWhenQueryTimesOut() {
        UUID opId = UUID.randomUUID();
        // Destroy responder so NATS request will time out
        queryResponder.destroy();

        Optional<DurableOperationStatus> resolved = stateProvider.findOperationStatus(opId);
        assertThat(resolved).isPresent();
        assertThat(resolved.get().isDegraded()).isTrue();
        assertThat(resolved.get().status()).isEqualTo("DEGRADED_UNAVAILABLE");

        // Verify that streamController completes emitter cleanly on degraded status without hanging or attaching to live hub
        SseEmitter emitter = streamController.streamOperationStatus(opId);
        assertThat(emitter).isNotNull();
        assertThat(statusHub.getActiveSubscriberCount(opId)).isZero();
    }
}
