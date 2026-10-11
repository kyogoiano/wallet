package br.com.wallet.copilot.internal.mcp.elicitation;

import br.com.wallet.copilot.api.ProposalUseCase;
import br.com.wallet.copilot.api.dto.ApproveProposalCommand;
import br.com.wallet.copilot.api.dto.ProposalResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service orchestrating secure Model Context Protocol (MCP) URL Elicitation (REQ-MCP-008, I-MCP-003).
 * Generates opaque, high-entropy, single-use HTTPS tokens and enforces independent authentication
 * and subject matching gates prior to domain proposal approval.
 */
@Service
public class McpUrlElicitationService {

    private static final String BASE_URL = "https://wallet.bank/copilot/approve?token=";
    private static final Duration TOKEN_TTL = Duration.ofMinutes(5);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private record TokenMetadata(
            UUID proposalId,
            String tenantId,
            String authorizedUser,
            Instant expiresAt
    ) {}

    private final ProposalUseCase proposalUseCase;
    private final Clock clock;
    private final Map<String, TokenMetadata> tokenRegistry = new ConcurrentHashMap<>();

    public McpUrlElicitationService(
            @NonNull final ProposalUseCase proposalUseCase,
            @NonNull final Clock clock
    ) {
        this.proposalUseCase = Objects.requireNonNull(proposalUseCase, "proposalUseCase cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @NonNull
    public String generateApprovalUrl(
            @NonNull final String tenantId,
            @NonNull final UUID proposalId,
            @NonNull final String authorizedUser
    ) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(proposalId, "proposalId cannot be null");
        Objects.requireNonNull(authorizedUser, "authorizedUser cannot be null");

        byte[] entropy = new byte[32];
        SECURE_RANDOM.nextBytes(entropy);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);

        Instant expiresAt = clock.instant().plus(TOKEN_TTL);
        tokenRegistry.put(token, new TokenMetadata(proposalId, tenantId, authorizedUser, expiresAt));

        return BASE_URL + token;
    }

    @NonNull
    public ProposalResponse consumeAndApprove(
            @NonNull final String token,
            @NonNull final String callerTenantId,
            @NonNull final String callerUser
    ) {
        Objects.requireNonNull(token, "token cannot be null");
        Objects.requireNonNull(callerTenantId, "callerTenantId cannot be null");
        Objects.requireNonNull(callerUser, "callerUser cannot be null");

        TokenMetadata meta = tokenRegistry.remove(token);
        if (meta == null) {
            throw new SecurityException("Invalid or consumed token");
        }

        if (meta.expiresAt().isBefore(clock.instant())) {
            throw new SecurityException("Expired token");
        }

        if (!meta.tenantId().equals(callerTenantId)) {
            throw new SecurityException("Tenant mismatch");
        }

        if (!meta.authorizedUser().equals(callerUser)) {
            throw new SecurityException("User mismatch: caller is not the authorized owner of this proposal");
        }

        return proposalUseCase.approveProposal(
                callerTenantId,
                callerUser,
                new ApproveProposalCommand(meta.proposalId())
        );
    }
}
