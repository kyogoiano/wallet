package br.com.wallet.unit.edge;

import br.com.wallet.edge.api.EdgeCommandIngress;
import br.com.wallet.edge.api.EdgeCommandResult;
import br.com.wallet.edge.api.CommandEnvelope;
import br.com.wallet.edge.internal.ingress.EdgeOperationsController;
import br.com.wallet.edge.internal.ingress.OperationStatusResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("EdgeOperationsController Unit Tests")
class EdgeOperationsControllerTest {

    private EdgeCommandIngress ingress;
    private EdgeOperationsController controller;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        ingress = mock(EdgeCommandIngress.class);
        controller = new EdgeOperationsController(ingress);
        request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
    }

    @Test
    @DisplayName("Should return 202 ACCEPTED with Location header when command is accepted")
    void shouldReturn202OnAccepted() throws Exception {
        UUID opId = UUID.randomUUID();
        when(ingress.acceptCommand(any(CommandEnvelope.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        new EdgeCommandResult.Accepted(opId, "/operations/" + opId, false)
                ));

        ResponseEntity<?> response = controller.acceptTransfer(opId, "{\"amount\": 100.00}", request).get();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getHeaders().getLocation()).isEqualTo(URI.create("/operations/" + opId));
        assertThat(response.getHeaders().getFirst("X-Edge-Spooled")).isEqualTo("false");
        assertThat(response.getBody()).isInstanceOf(OperationStatusResponse.class);
    }

    @Test
    @DisplayName("Should return 429 with Retry-After when rate limited")
    void shouldReturn429OnRateLimited() throws Exception {
        when(ingress.acceptCommand(any(CommandEnvelope.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        new EdgeCommandResult.RateLimited("Rate limited", 1)
                ));

        ResponseEntity<?> response = controller.acceptDeposit(null, "{\"amount\": 50.00}", request).get();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("1");
    }

    @Test
    @DisplayName("Should return 413 when payload exceeds limit")
    void shouldReturn413OnPayloadTooLarge() throws Exception {
        when(ingress.acceptCommand(any(CommandEnvelope.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        new EdgeCommandResult.ContentTooLarge(70000, 65536)
                ));

        ResponseEntity<?> response = controller.acceptWithdrawal(null, "{}", request).get();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
    }

    @Test
    @DisplayName("Should return 503 with Retry-After: 5 when spool is saturated")
    void shouldReturn503OnSaturated() throws Exception {
        when(ingress.acceptCommand(any(CommandEnvelope.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        new EdgeCommandResult.Saturated("Spool full", 5)
                ));

        ResponseEntity<?> response = controller.acceptTransfer(null, "{}", request).get();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("5");
    }
}
