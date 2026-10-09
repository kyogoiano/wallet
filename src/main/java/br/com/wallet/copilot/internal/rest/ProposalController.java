package br.com.wallet.copilot.internal.rest;

import br.com.wallet.core.exceptions.TenantContextMissingException;
import br.com.wallet.copilot.api.ProposalUseCase;
import br.com.wallet.copilot.api.dto.ApproveProposalCommand;
import br.com.wallet.copilot.api.dto.CreateProposalCommand;
import br.com.wallet.copilot.api.dto.ProposalResponse;
import br.com.wallet.copilot.api.dto.RejectProposalCommand;
import br.com.wallet.copilot.api.exception.IdempotencyConflictException;
import br.com.wallet.copilot.api.exception.ProposalConflictException;
import br.com.wallet.copilot.api.exception.ProposalExpiredException;
import br.com.wallet.copilot.api.exception.ProposalNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/copilot/proposals")
public class ProposalController {

    private static final Logger log = LoggerFactory.getLogger(ProposalController.class);

    private final ProposalUseCase proposalUseCase;

    @Autowired
    public ProposalController(@NonNull final ProposalUseCase proposalUseCase) {
        this.proposalUseCase = Objects.requireNonNull(proposalUseCase, "proposalUseCase cannot be null");
    }

    @PostMapping
    public ResponseEntity<ProposalResponse> createProposal(
            @RequestBody @NonNull final CreateProposalCommand command,
            @NonNull final HttpServletRequest request
    ) {
        String tenantId = resolveTenantId(request);
        String principal = resolvePrincipal(request);
        log.info("Creating proposal for tenant={}, createdBy={}", tenantId, principal);

        ProposalResponse response = proposalUseCase.createProposal(tenantId, principal, command);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{proposalId}/approve")
    public ResponseEntity<ProposalResponse> approveProposal(
            @PathVariable @NonNull final UUID proposalId,
            @NonNull final HttpServletRequest request
    ) {
        String tenantId = resolveTenantId(request);
        String principal = resolvePrincipal(request);
        log.info("Approving proposal {} for tenant={}, approvedBy={}", proposalId, tenantId, principal);

        ProposalResponse response = proposalUseCase.approveProposal(tenantId, principal, new ApproveProposalCommand(proposalId));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{proposalId}/reject")
    public ResponseEntity<ProposalResponse> rejectProposal(
            @PathVariable @NonNull final UUID proposalId,
            @RequestBody(required = false) final Map<String, String> body,
            @NonNull final HttpServletRequest request
    ) {
        String tenantId = resolveTenantId(request);
        String principal = resolvePrincipal(request);
        String reason = body != null ? body.get("reason") : null;
        log.info("Rejecting proposal {} for tenant={}, rejectedBy={}", proposalId, tenantId, principal);

        ProposalResponse response = proposalUseCase.rejectProposal(tenantId, principal, new RejectProposalCommand(proposalId, reason));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{proposalId}")
    public ResponseEntity<ProposalResponse> getProposal(
            @PathVariable @NonNull final UUID proposalId,
            @NonNull final HttpServletRequest request
    ) {
        String tenantId = resolveTenantId(request);
        return proposalUseCase.findById(proposalId, tenantId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<ProposalResponse>> listPending(
            @RequestParam @NonNull final UUID walletId,
            @NonNull final HttpServletRequest request
    ) {
        String tenantId = resolveTenantId(request);
        List<ProposalResponse> pending = proposalUseCase.listPending(tenantId, walletId);
        return ResponseEntity.ok(pending);
    }

    @ExceptionHandler({TenantContextMissingException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, String>> handleBadRequest(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(ProposalNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ProposalNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler({ProposalConflictException.class, ProposalExpiredException.class, IdempotencyConflictException.class})
    public ResponseEntity<Map<String, String>> handleConflict(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
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

        throw new TenantContextMissingException("Authenticated tenant context is required");
    }

    private String resolvePrincipal(final HttpServletRequest request) {
        if (request.getUserPrincipal() != null) {
            return request.getUserPrincipal().getName();
        }
        String userHeader = request.getHeader("X-User-Id");
        if (userHeader != null && !userHeader.isBlank()) {
            return userHeader.trim();
        }
        Object principalAttr = request.getAttribute("wallet.authenticated_principal");
        if (principalAttr != null) {
            return principalAttr.toString();
        }
        return "anonymous-user";
    }
}
