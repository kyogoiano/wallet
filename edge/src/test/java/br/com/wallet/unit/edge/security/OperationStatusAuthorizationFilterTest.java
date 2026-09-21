package br.com.wallet.unit.edge.security;

import br.com.wallet.edge.api.AuthenticatedPrincipal;
import br.com.wallet.edge.api.OperationAuthorizationProvider;
import br.com.wallet.edge.internal.ingress.OperationStatusAuthorizationFilter;
import br.com.wallet.edge.internal.security.HmacAuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("OperationStatusAuthorizationFilter Tests (REQ-SEC-007, TASK-SEC-3.3)")
class OperationStatusAuthorizationFilterTest {

    private OperationAuthorizationProvider authorizationProvider;
    private OperationStatusAuthorizationFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        authorizationProvider = mock(OperationAuthorizationProvider.class);
        filter = new OperationStatusAuthorizationFilter(authorizationProvider);
        filterChain = mock(FilterChain.class);
    }

    @Test
    @DisplayName("REQ-SEC-007: Must use tenant from AuthenticatedPrincipal attribute and verify with authorization provider")
    void shouldUseAuthenticatedPrincipalTenant() throws ServletException, IOException {
        UUID opId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/operations/" + opId + "/stream");

        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                "principal-1", "tenant-authenticated", "key-1", Set.of()
        );
        request.setAttribute(HmacAuthenticationFilter.AUTHENTICATED_PRINCIPAL_ATTR, principal);

        when(authorizationProvider.isAuthorized(opId, "tenant-authenticated")).thenReturn(true);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(authorizationProvider).isAuthorized(opId, "tenant-authenticated");
    }

    @Test
    @DisplayName("REQ-SEC-007: Must reject cross-tenant snooping with 403 Forbidden (FORBIDDEN_TENANT_ACCESS)")
    void shouldRejectCrossTenantSnoopingWith403() throws ServletException, IOException {
        UUID opId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/operations/" + opId + "/stream");

        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                "attacker", "tenant-attacker", "key-attacker", Set.of()
        );
        request.setAttribute(HmacAuthenticationFilter.AUTHENTICATED_PRINCIPAL_ATTR, principal);

        // Attacker does not own this operation
        when(authorizationProvider.isAuthorized(opId, "tenant-attacker")).thenReturn(false);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(response.getContentAsString()).contains("FORBIDDEN_TENANT_ACCESS");
        verifyNoInteractions(filterChain);
    }
}
