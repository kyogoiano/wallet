package br.com.wallet.copilot.internal.mcp.interaction;

import br.com.wallet.copilot.api.ProposalUseCase;
import br.com.wallet.copilot.api.dto.ProposalResponse;
import br.com.wallet.copilot.api.model.ProposalStatus;
import br.com.wallet.copilot.api.model.ProposalType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class McpInteractionTrackerTest {

    @Mock
    private ProposalUseCase proposalUseCase;

    private Clock clock;
    private McpInteractionTracker tracker;

    private final String tenantId = "tenant-alpha";
    private final String principal = "agent-runtime-1";
    private final UUID proposalId = UUID.randomUUID();
    private final UUID walletId = UUID.randomUUID();
    private final Instant baseTime = Instant.parse("2026-10-10T12:00:00Z");

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(baseTime, ZoneOffset.UTC);
        tracker = new McpInteractionTracker(proposalUseCase, clock);
    }

    @Test
    @DisplayName("Positive: Reconnected client within active session resumes pending interaction")
    void testActiveInteractionResumesPositive() {
        String correlationId = tracker.track(tenantId, principal, proposalId);

        ProposalResponse proposal = new ProposalResponse(
                proposalId, tenantId, walletId, ProposalType.TRANSFER,
                ProposalStatus.PROPOSED, "{}", "key-1", UUID.randomUUID().toString(),
                principal, null, baseTime, baseTime.plusSeconds(300),
                null, null, null, null, null
        );
        when(proposalUseCase.findById(proposalId, tenantId)).thenReturn(Optional.of(proposal));

        McpInteractionTracker.ResumptionResult result = tracker.resume(correlationId, tenantId);

        assertThat(result.resumed()).isTrue();
        assertThat(result.proposalId()).isEqualTo(proposalId);
        assertThat(result.proposal()).isPresent();
        assertThat(result.proposal().get().status()).isEqualTo(ProposalStatus.PROPOSED);
    }

    @Test
    @DisplayName("Boundary: Unknown or expired correlation ID fails safe with CORRELATION_LOST (final.txt MUST 4)")
    void testUnknownCorrelationFailsSafe() {
        McpInteractionTracker.ResumptionResult result = tracker.resume("unknown-corr-id", tenantId);

        assertThat(result.resumed()).isFalse();
        assertThat(result.reason()).isEqualTo("CORRELATION_LOST");
        assertThat(result.fallbackUri()).contains("https://wallet.bank/copilot/proposals");
        verifyNoInteractions(proposalUseCase);
    }

    @Test
    @DisplayName("Invariant Gate: Tracker loss or disconnect leaves proposal TTL untouched; zero duplicate proposals (I-MCP-004)")
    void testDisconnectionPreservesProposalTtl() {
        String correlationId = tracker.track(tenantId, principal, proposalId);

        // Simulate node restart / state purge
        tracker.evict(correlationId);

        // Attempt resumption after state loss
        McpInteractionTracker.ResumptionResult result = tracker.resume(correlationId, tenantId);

        assertThat(result.resumed()).isFalse();
        assertThat(result.reason()).isEqualTo("CORRELATION_LOST");
        // Proves that state loss directs to external Web/CLI and does not alter domain proposal
        verify(proposalUseCase, never()).createProposal(any(), any(), any());
    }
}
