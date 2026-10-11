package br.com.wallet.unit.edge;

import br.com.wallet.edge.api.DurableOperationStateProvider;
import br.com.wallet.edge.internal.ingress.EdgeOperationsStreamController;
import br.com.wallet.edge.internal.ingress.OperationStatusHub;
import br.com.wallet.edge.internal.ingress.OperationStatusResponse;
import br.com.wallet.edge.internal.transport.AltSvcWebFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("Edge Streaming & Multi-Protocol Transport Unit Tests (TASK-5.1, REQ-EDG-019, REQ-EDG-020)")
class EdgeStreamAndTransportTest {

    private OperationStatusHub statusHub;
    private DurableOperationStateProvider durableOperationStateProvider;
    private EdgeOperationsStreamController streamController;
    private AltSvcWebFilter altSvcFilter;

    @BeforeEach
    void setUp() {
        statusHub = new OperationStatusHub();
        durableOperationStateProvider = mock(DurableOperationStateProvider.class);
        streamController = new EdgeOperationsStreamController(statusHub, durableOperationStateProvider);
        altSvcFilter = new AltSvcWebFilter();
    }

    @Test
    @DisplayName("Should register emitter and broadcast terminal COMPLETED event via SSE (REQ-EDG-019)")
    void shouldRegisterAndBroadcastSseEvents() {
        UUID opId = UUID.randomUUID();

        SseEmitter emitter = streamController.streamOperationStatus(opId);
        assertThat(emitter).isNotNull();
        assertThat(statusHub.getActiveSubscriberCount(opId)).isEqualTo(1);

        // Broadcast non-terminal update
        statusHub.publishStatus(opId, OperationStatusResponse.STATUS_PROCESSING, "Processing batch");
        assertThat(statusHub.getActiveSubscriberCount(opId)).isEqualTo(1);

        // Broadcast terminal COMPLETED update -> must complete and remove emitter
        statusHub.publishStatus(opId, OperationStatusResponse.STATUS_COMPLETED, "Settled successfully");
        assertThat(statusHub.getActiveSubscriberCount(opId)).isEqualTo(0);
    }

    @Test
    @DisplayName("Should inject Alt-Svc header for HTTP/3 QUIC upgrade (REQ-EDG-020)")
    void shouldInjectAltSvcHeader() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        altSvcFilter.doFilter(request, response, filterChain);

        verify(response).setHeader(
                eq(AltSvcWebFilter.ALT_SVC_HEADER),
                eq("h3=\":8443\"; ma=86400; persist=1")
        );
        verify(filterChain).doFilter(request, response);
    }
}
