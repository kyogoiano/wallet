package br.com.wallet.copilot.internal.mcp.interaction;

import br.com.wallet.copilot.api.ProposalUseCase;
import br.com.wallet.copilot.api.dto.ProposalResponse;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ephemeral Multi-Round-Trip Request (MRTR) interaction tracker for MCP clients (REQ-MCP-007, I-MCP-004).
 * Safe for multi-instance deployments: state loss or cross-instance routing safely falls back to external Web/CLI
 * without duplicating proposals, extending TTL, or repeating execution.
 */
@Component
public class McpInteractionTracker {

    private static final Duration CORRELATION_TTL = Duration.ofMinutes(10);
    private static final String DEFAULT_FALLBACK_URI = "https://wallet.bank/copilot/proposals/review";

    private record InteractionRecord(
            String tenantId,
            String principal,
            UUID proposalId,
            Instant createdAt,
            Instant expiresAt
    ) {}

    public record ResumptionResult(
            boolean resumed,
            @Nullable UUID proposalId,
            @Nullable Optional<ProposalResponse> proposal,
            @Nullable String reason,
            @Nullable String fallbackUri
    ) {
        public static ResumptionResult success(UUID proposalId, Optional<ProposalResponse> proposal) {
            return new ResumptionResult(true, proposalId, proposal, null, null);
        }

        public static ResumptionResult failure(String reason, String fallbackUri) {
            return new ResumptionResult(false, null, Optional.empty(), reason, fallbackUri);
        }
    }

    private final ProposalUseCase proposalUseCase;
    private final Clock clock;
    private final Map<String, InteractionRecord> correlations = new ConcurrentHashMap<>();

    public McpInteractionTracker(
            @NonNull final ProposalUseCase proposalUseCase,
            @NonNull final Clock clock
    ) {
        this.proposalUseCase = Objects.requireNonNull(proposalUseCase, "proposalUseCase cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @NonNull
    public String track(
            @NonNull final String tenantId,
            @NonNull final String principal,
            @NonNull final UUID proposalId
    ) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(principal, "principal cannot be null");
        Objects.requireNonNull(proposalId, "proposalId cannot be null");

        String correlationId = UUID.randomUUID().toString();
        Instant now = clock.instant();
        correlations.put(correlationId, new InteractionRecord(tenantId, principal, proposalId, now, now.plus(CORRELATION_TTL)));
        return correlationId;
    }

    @NonNull
    public ResumptionResult resume(
            @NonNull final String correlationId,
            @NonNull final String callerTenantId
    ) {
        Objects.requireNonNull(correlationId, "correlationId cannot be null");
        Objects.requireNonNull(callerTenantId, "callerTenantId cannot be null");

        InteractionRecord record = correlations.get(correlationId);
        if (record == null || record.expiresAt().isBefore(clock.instant())) {
            correlations.remove(correlationId);
            return ResumptionResult.failure("CORRELATION_LOST", DEFAULT_FALLBACK_URI);
        }

        if (!record.tenantId().equals(callerTenantId)) {
            return ResumptionResult.failure("TENANT_MISMATCH", DEFAULT_FALLBACK_URI);
        }

        Optional<ProposalResponse> proposal = proposalUseCase.findById(record.proposalId(), callerTenantId);
        return ResumptionResult.success(record.proposalId(), proposal);
    }

    public void evict(@NonNull final String correlationId) {
        Objects.requireNonNull(correlationId, "correlationId cannot be null");
        correlations.remove(correlationId);
    }
}
