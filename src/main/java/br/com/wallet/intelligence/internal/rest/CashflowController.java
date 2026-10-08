package br.com.wallet.intelligence.internal.rest;

import br.com.wallet.core.exceptions.TenantContextMissingException;
import br.com.wallet.intelligence.api.dto.CashflowProjectionResponse;
import br.com.wallet.intelligence.api.dto.CashflowSyncResponse;
import br.com.wallet.intelligence.internal.service.CashflowForecastingService;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;
import java.util.UUID;

/**
 * REST ingress controller for cashflow liquidity projections and Goals synchronization (REQ-CASH-004, REQ-CASH-005).
 * Strictly scoped to authenticated tenant context (I-CASH-007).
 */
@RestController
@RequestMapping("/api/v1/intelligence/cashflow")
public class CashflowController {

    private static final Logger log = LoggerFactory.getLogger(CashflowController.class);

    private final CashflowForecastingService forecastingService;

    @Autowired
    public CashflowController(@NonNull final CashflowForecastingService forecastingService) {
        this.forecastingService = Objects.requireNonNull(forecastingService, "forecastingService cannot be null");
    }

    @GetMapping("/{walletId}/projections")
    public ResponseEntity<CashflowProjectionResponse> getProjections(
            @PathVariable @NonNull final UUID walletId,
            @NonNull final HttpServletRequest request
    ) {
        Objects.requireNonNull(walletId, "walletId cannot be null");
        Objects.requireNonNull(request, "request cannot be null");

        String tenantId = resolveTenantId(request);
        log.debug("Evaluating cashflow projections for tenant={}, walletId={}", tenantId, walletId);

        CashflowProjectionResponse response = forecastingService.getProjections(tenantId, walletId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{walletId}/sync-goals")
    public ResponseEntity<CashflowSyncResponse> syncGoals(
            @PathVariable @NonNull final UUID walletId,
            @NonNull final HttpServletRequest request
    ) {
        Objects.requireNonNull(walletId, "walletId cannot be null");
        Objects.requireNonNull(request, "request cannot be null");

        String tenantId = resolveTenantId(request);
        log.info("Synchronizing committed expenses to Goals for tenant={}, walletId={}", tenantId, walletId);

        CashflowSyncResponse response = forecastingService.syncGoals(tenantId, walletId);
        return ResponseEntity.ok(response);
    }

    private String resolveTenantId(final HttpServletRequest request) {
        Object principalAttr = request.getAttribute("wallet.authenticated_principal");
        if (principalAttr != null) {
            if (principalAttr instanceof String s && !s.isBlank()) {
                return s.trim();
            }
            try {
                var method = principalAttr.getClass().getMethod("tenantId");
                Object val = method.invoke(principalAttr);
                if (val != null && !val.toString().isBlank()) {
                    return val.toString().trim();
                }
            } catch (Exception ignored) {
            }
        }

        Object tenantAttr = request.getAttribute("wallet.tenant_id");
        if (tenantAttr instanceof String s && !s.isBlank()) {
            return s.trim();
        }

        String headerTenant = request.getHeader("X-Tenant-Id");
        if (headerTenant != null && !headerTenant.isBlank()) {
            return headerTenant.trim();
        }

        throw new TenantContextMissingException("Authenticated tenant context is required for cashflow queries");
    }
}
