package br.com.wallet.intelligence.internal.rest;

import br.com.wallet.infrastructure.rest.exception.ApiExceptionHandler;
import br.com.wallet.intelligence.api.model.Cadence;
import br.com.wallet.intelligence.api.model.PriceState;
import br.com.wallet.intelligence.api.model.SubscriptionStatus;
import br.com.wallet.intelligence.api.model.VarianceType;
import br.com.wallet.intelligence.internal.domain.Subscription;
import br.com.wallet.intelligence.internal.persistence.SubscriptionDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionController Unit Tests (Phase 4, REQ-SUB-008, I-SUB-010)")
class SubscriptionControllerTest {

    @Mock
    private SubscriptionDao subscriptionDao;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SubscriptionController controller = new SubscriptionController(subscriptionDao);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("REQ-SUB-008, I-SUB-010: GET subscriptions resolves tenant from authenticated context")
    void shouldReturnSubscriptionsForAuthenticatedTenant() throws Exception {
        UUID walletId = UUID.randomUUID();
        UUID cpId = UUID.randomUUID();
        String tenantId = "tenant-alpha";
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        Subscription sub = new Subscription(
                UUID.randomUUID(), tenantId, walletId, cpId,
                Cadence.MONTHLY, SubscriptionStatus.ACTIVE, PriceState.NORMAL,
                "STREAMING", new BigDecimal("49.90"), new BigDecimal("49.90"),
                new BigDecimal("1.000000"), 3, VarianceType.FIXED,
                now.plus(30, ChronoUnit.DAYS), now, now, now
        );

        when(subscriptionDao.findByWalletId(eq(tenantId), eq(walletId), eq(null)))
                .thenReturn(List.of(sub));

        mockMvc.perform(get("/api/v1/intelligence/subscriptions/{walletId}", walletId)
                        .header("X-Tenant-Id", tenantId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tenantId").value(tenantId))
                .andExpect(jsonPath("$[0].walletId").value(walletId.toString()))
                .andExpect(jsonPath("$[0].counterpartyId").value(cpId.toString()))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].cadence").value("MONTHLY"))
                .andExpect(jsonPath("$[0].averageAmount").value(49.90));

        verify(subscriptionDao).findByWalletId(tenantId, walletId, null);
    }

    @Test
    @DisplayName("REQ-SUB-008: GET subscriptions with ?status= filter passes status enum to DAO")
    void shouldFilterSubscriptionsByStatus() throws Exception {
        UUID walletId = UUID.randomUUID();
        String tenantId = "tenant-beta";

        when(subscriptionDao.findByWalletId(eq(tenantId), eq(walletId), eq(SubscriptionStatus.CANDIDATE)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/intelligence/subscriptions/{walletId}", walletId)
                        .param("status", "CANDIDATE")
                        .header("X-Tenant-Id", tenantId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        verify(subscriptionDao).findByWalletId(tenantId, walletId, SubscriptionStatus.CANDIDATE);
    }

    @Test
    @DisplayName("I-SUB-010: Client-supplied ?tenantId= query param is ignored in favor of authenticated context")
    void shouldIgnoreClientSuppliedTenantParam() throws Exception {
        UUID walletId = UUID.randomUUID();
        String authenticatedTenant = "tenant-real";

        when(subscriptionDao.findByWalletId(eq(authenticatedTenant), eq(walletId), eq(null)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/intelligence/subscriptions/{walletId}", walletId)
                        .param("tenantId", "tenant-attacker-forged")
                        .header("X-Tenant-Id", authenticatedTenant)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(subscriptionDao).findByWalletId(authenticatedTenant, walletId, null);
        verify(subscriptionDao, never()).findByWalletId(eq("tenant-attacker-forged"), any(), any());
    }

    @Test
    @DisplayName("I-SUB-010: Missing authenticated tenant context rejects request")
    void shouldRejectRequestWithoutTenantContext() throws Exception {
        UUID walletId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/intelligence/subscriptions/{walletId}", walletId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(subscriptionDao);
    }
}
