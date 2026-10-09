package br.com.wallet.copilot;

import br.com.wallet.copilot.api.model.ProposalStatus;
import br.com.wallet.copilot.api.model.ProposalType;
import br.com.wallet.copilot.internal.model.FinancialProposal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Proposal State Machine & Invariant Tests (TASK-4.2)")
class ProposalStateMachineTest {

    private final UUID proposalId = UUID.randomUUID();
    private final UUID walletId = UUID.randomUUID();
    private final String tenantId = "tenant-alpha";
    private final Instant now = Instant.now();

    private FinancialProposal createProposal(ProposalStatus status, Instant expiresAt, Instant leaseUntil) {
        return new FinancialProposal(
                proposalId,
                tenantId,
                walletId,
                ProposalType.TRANSFER,
                "{\"amount\":100.00}",
                "hash-123",
                status,
                "idem-key-1",
                UUID.randomUUID().toString(),
                "agent-user",
                null,
                now.minus(1, ChronoUnit.MINUTES),
                expiresAt,
                null,
                leaseUntil,
                null,
                null,
                null
        );
    }

    @Test
    @DisplayName("Should allow claim when status is PROPOSED and before expiration")
    void shouldAllowClaimWhenProposedAndNotExpired() {
        FinancialProposal proposal = createProposal(
                ProposalStatus.PROPOSED,
                now.plus(15, ChronoUnit.MINUTES),
                null
        );

        assertThat(proposal.canBeClaimed(now)).isTrue();
        assertThat(proposal.isExpired(now)).isFalse();
    }

    @Test
    @DisplayName("Should reject claim when proposal has expired")
    void shouldRejectClaimWhenExpired() {
        FinancialProposal proposal = createProposal(
                ProposalStatus.PROPOSED,
                now.minus(1, ChronoUnit.SECONDS),
                null
        );

        assertThat(proposal.canBeClaimed(now)).isFalse();
        assertThat(proposal.isExpired(now)).isTrue();
    }

    @Test
    @DisplayName("Should reject claim when status is not PROPOSED")
    void shouldRejectClaimWhenNotProposed() {
        for (ProposalStatus status : ProposalStatus.values()) {
            if (status != ProposalStatus.PROPOSED) {
                FinancialProposal proposal = createProposal(
                        status,
                        now.plus(15, ChronoUnit.MINUTES),
                        null
                );
                assertThat(proposal.canBeClaimed(now))
                        .as("Status %s should not be claimable", status)
                        .isFalse();
            }
        }
    }

    @Test
    @DisplayName("Should allow rejection only when status is PROPOSED")
    void shouldAllowRejectionOnlyWhenProposed() {
        FinancialProposal proposed = createProposal(ProposalStatus.PROPOSED, now.plus(15, ChronoUnit.MINUTES), null);
        assertThat(proposed.canBeRejected()).isTrue();

        FinancialProposal executing = createProposal(ProposalStatus.EXECUTING, now.plus(15, ChronoUnit.MINUTES), null);
        assertThat(executing.canBeRejected()).isFalse();

        FinancialProposal executed = createProposal(ProposalStatus.EXECUTED, now.plus(15, ChronoUnit.MINUTES), null);
        assertThat(executed.canBeRejected()).isFalse();
    }

    @Test
    @DisplayName("Should detect stale lease when EXECUTING and now is past lease until")
    void shouldDetectStaleLease() {
        FinancialProposal proposal = createProposal(
                ProposalStatus.EXECUTING,
                now.plus(15, ChronoUnit.MINUTES),
                now.minus(5, ChronoUnit.SECONDS)
        );

        assertThat(proposal.isLeaseExpired(now)).isTrue();
    }

    @Test
    @DisplayName("Should not detect stale lease when lease is still valid")
    void shouldNotDetectStaleLeaseWhenStillValid() {
        FinancialProposal proposal = createProposal(
                ProposalStatus.EXECUTING,
                now.plus(15, ChronoUnit.MINUTES),
                now.plus(2, ChronoUnit.MINUTES)
        );

        assertThat(proposal.isLeaseExpired(now)).isFalse();
    }

    @Test
    @DisplayName("Should correctly identify terminal states")
    void shouldCorrectlyIdentifyTerminalStates() {
        assertThat(ProposalStatus.EXECUTED.isTerminal()).isTrue();
        assertThat(ProposalStatus.REJECTED.isTerminal()).isTrue();
        assertThat(ProposalStatus.EXPIRED.isTerminal()).isTrue();
        assertThat(ProposalStatus.INVALIDATED.isTerminal()).isTrue();
        assertThat(ProposalStatus.PROPOSED.isTerminal()).isFalse();
        assertThat(ProposalStatus.EXECUTING.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("Should enforce null safety on mandatory fields")
    void shouldEnforceNullSafety() {
        assertThatThrownBy(() -> new FinancialProposal(
                null, tenantId, walletId, ProposalType.TRANSFER, "{}", "hash",
                ProposalStatus.PROPOSED, "idem", "op", "user", null,
                now, now.plus(1, ChronoUnit.HOURS), null, null, null, null, null
        )).isInstanceOf(NullPointerException.class);
    }
}
