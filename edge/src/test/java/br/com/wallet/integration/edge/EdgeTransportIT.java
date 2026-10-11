package br.com.wallet.integration.edge;

import br.com.wallet.edge.internal.transport.AltSvcWebFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("EdgeTransportIT: HTTP/3 QUIC Transport Policy & 0-RTT Replay Gate (TASK-5.4, TASK-5.5, REQ-EDG-020)")
class EdgeTransportIT {

    private AltSvcWebFilter transportFilter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        transportFilter = new AltSvcWebFilter();
        filterChain = mock(FilterChain.class);
    }

    @Test
    @DisplayName("Should reject financial mutation over HTTP/3 0-RTT early data with HTTP 425 Too Early (REQ-EDG-020)")
    void shouldRejectFinancialMutationOverZeroRtt() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/operations/transfers");
        request.addHeader(AltSvcWebFilter.EARLY_DATA_HEADER, "1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        transportFilter.doFilter(request, response, filterChain);

        // Invariant REQ-EDG-020: 0-RTT MUST NOT process financial mutations -> HTTP 425 Too Early
        assertThat(response.getStatus()).isEqualTo(AltSvcWebFilter.HTTP_TOO_EARLY);
        assertThat(response.getHeader(AltSvcWebFilter.ALT_SVC_HEADER))
                .isEqualTo(AltSvcWebFilter.DEFAULT_ALT_SVC_VALUE);
        assertThat(response.getContentAsString()).contains("1-RTT");

        // Execution halted before reaching command controller
        verifyNoInteractions(filterChain);
    }

    @Test
    @DisplayName("Should allow financial mutation over 1-RTT handshake (without Early-Data header)")
    void shouldAllowFinancialMutationOverOneRtt() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/operations/transfers");
        MockHttpServletResponse response = new MockHttpServletResponse();

        transportFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isNotEqualTo(AltSvcWebFilter.HTTP_TOO_EARLY);
        assertThat(response.getHeader(AltSvcWebFilter.ALT_SVC_HEADER))
                .isEqualTo(AltSvcWebFilter.DEFAULT_ALT_SVC_VALUE);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should permit safe read streams over HTTP/3 0-RTT early data")
    void shouldPermitReadStreamOverZeroRtt() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/operations/11111111-1111-1111-1111-111111111111/stream");
        request.addHeader(AltSvcWebFilter.EARLY_DATA_HEADER, "1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        transportFilter.doFilter(request, response, filterChain);

        // Safe idempotent GET queries are permissible over 0-RTT
        assertThat(response.getStatus()).isNotEqualTo(AltSvcWebFilter.HTTP_TOO_EARLY);
        verify(filterChain).doFilter(request, response);
    }
}
