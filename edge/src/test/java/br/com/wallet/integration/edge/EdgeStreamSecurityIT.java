package br.com.wallet.integration.edge;

import br.com.wallet.edge.api.OperationAuthorizationProvider;
import br.com.wallet.edge.internal.ingress.OperationStatusAuthorizationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("EdgeStreamSecurityIT: SSE Subscriber Tenant Authorization Integration Test (TASK-5.10, TASK-5.11, REQ-EDG-019)")
class EdgeStreamSecurityIT {

    private OperationAuthorizationProvider authorizationProvider;
    private OperationStatusAuthorizationFilter securityFilter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        authorizationProvider = mock(OperationAuthorizationProvider.class);
        securityFilter = new OperationStatusAuthorizationFilter(authorizationProvider);
        filterChain = mock(FilterChain.class);
    }

    @Test
    @DisplayName("Should reject SSE stream request with 401 Unauthorized when X-Tenant-Id is missing (TASK-5.10)")
    void shouldRejectWhenTenantHeaderMissing() throws ServletException, IOException {
        UUID opId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/operations/" + opId + "/stream");
        MockHttpServletResponse response = new MockHttpServletResponse();

        securityFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getContentAsString()).contains("UNAUTHORIZED");
        verifyNoInteractions(filterChain);
    }

    @Test
    @DisplayName("Should reject SSE stream request with 403 Forbidden when tenant is not authorized for operation (TASK-5.10)")
    void shouldRejectWhenTenantNotAuthorized() throws ServletException, IOException {
        UUID opId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/operations/" + opId + "/stream");
        request.addHeader(OperationStatusAuthorizationFilter.TENANT_HEADER, "tenant-unauthorized");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(authorizationProvider.isAuthorized(opId, "tenant-unauthorized")).thenReturn(false);

        securityFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(response.getContentAsString()).contains("FORBIDDEN");
        verify(authorizationProvider).isAuthorized(opId, "tenant-unauthorized");
        verifyNoInteractions(filterChain);
    }

    @Test
    @DisplayName("Should permit SSE stream request when tenant is authorized for operation (TASK-5.11)")
    void shouldPermitWhenTenantAuthorized() throws ServletException, IOException {
        UUID opId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/operations/" + opId + "/stream");
        request.addHeader(OperationStatusAuthorizationFilter.TENANT_HEADER, "tenant-authorized");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(authorizationProvider.isAuthorized(opId, "tenant-authorized")).thenReturn(true);

        securityFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        verify(authorizationProvider).isAuthorized(opId, "tenant-authorized");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should ignore non-streaming endpoints and proceed without tenant check")
    void shouldIgnoreNonStreamingEndpoints() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health/readiness");
        MockHttpServletResponse response = new MockHttpServletResponse();

        securityFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(authorizationProvider);
    }
}
