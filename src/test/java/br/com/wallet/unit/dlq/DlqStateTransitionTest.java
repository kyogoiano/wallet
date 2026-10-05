package br.com.wallet.unit.dlq;

import br.com.wallet.dlq.api.model.DlqStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DlqStateTransitionTest (TASK-1.5, I-TDLQ-004)")
class DlqStateTransitionTest {

    @Test
    @DisplayName("Assert terminal disjunction EXHAUSTED != QUARANTINED (I-TDLQ-004)")
    void shouldAssertTerminalDisjunction() {
        assertThat(DlqStatus.EXHAUSTED).isNotEqualTo(DlqStatus.QUARANTINED);
        assertThat(DlqStatus.valueOf("QUARANTINED")).isEqualTo(DlqStatus.QUARANTINED);
        assertThat(DlqStatus.valueOf("EXHAUSTED")).isEqualTo(DlqStatus.EXHAUSTED);

        // Neither EXHAUSTED nor QUARANTINED is eligible for automated background replay
        assertThat(DlqStatus.EXHAUSTED.isAutomatedRetryEligible()).isFalse();
        assertThat(DlqStatus.QUARANTINED.isAutomatedRetryEligible()).isFalse();

        // Both are non-automated / terminal states requiring manual operator intervention
        assertThat(DlqStatus.EXHAUSTED.isTerminal()).isTrue();
        assertThat(DlqStatus.QUARANTINED.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("Only PENDING and FAILED states are eligible for automated replay")
    void shouldVerifyAutomatedRetryEligibility() {
        assertThat(DlqStatus.PENDING.isAutomatedRetryEligible()).isTrue();
        assertThat(DlqStatus.FAILED.isAutomatedRetryEligible()).isTrue();

        assertThat(DlqStatus.PROCESSING.isAutomatedRetryEligible()).isFalse();
        assertThat(DlqStatus.COMPLETED.isAutomatedRetryEligible()).isFalse();
        assertThat(DlqStatus.DISCARDED.isAutomatedRetryEligible()).isFalse();
    }

    @Test
    @DisplayName("Validate allowed transitions from PROCESSING")
    void shouldValidateProcessingTransitions() {
        assertThat(DlqStatus.PROCESSING.canTransitionTo(DlqStatus.COMPLETED)).isTrue();
        assertThat(DlqStatus.PROCESSING.canTransitionTo(DlqStatus.FAILED)).isTrue();
        assertThat(DlqStatus.PROCESSING.canTransitionTo(DlqStatus.EXHAUSTED)).isTrue();
        assertThat(DlqStatus.PROCESSING.canTransitionTo(DlqStatus.QUARANTINED)).isTrue();
        assertThat(DlqStatus.PROCESSING.canTransitionTo(DlqStatus.PENDING)).isFalse();
        assertThat(DlqStatus.PROCESSING.canTransitionTo(DlqStatus.DISCARDED)).isFalse();
    }

    @Test
    @DisplayName("Validate allowed transitions from QUARANTINED and EXHAUSTED")
    void shouldValidateQuarantinedAndExhaustedTransitions() {
        // Both can transition to PENDING (operator manual replay) or DISCARDED (operator discard)
        assertThat(DlqStatus.QUARANTINED.canTransitionTo(DlqStatus.PENDING)).isTrue();
        assertThat(DlqStatus.QUARANTINED.canTransitionTo(DlqStatus.DISCARDED)).isTrue();
        assertThat(DlqStatus.QUARANTINED.canTransitionTo(DlqStatus.PROCESSING)).isFalse();
        assertThat(DlqStatus.QUARANTINED.canTransitionTo(DlqStatus.COMPLETED)).isFalse();

        assertThat(DlqStatus.EXHAUSTED.canTransitionTo(DlqStatus.PENDING)).isTrue();
        assertThat(DlqStatus.EXHAUSTED.canTransitionTo(DlqStatus.DISCARDED)).isTrue();
        assertThat(DlqStatus.EXHAUSTED.canTransitionTo(DlqStatus.PROCESSING)).isFalse();
        assertThat(DlqStatus.EXHAUSTED.canTransitionTo(DlqStatus.COMPLETED)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = DlqStatus.class, names = {"COMPLETED", "DISCARDED"})
    @DisplayName("Terminal COMPLETED and DISCARDED cannot transition to any other status")
    void terminalStatesCannotTransition(DlqStatus terminalStatus) {
        for (DlqStatus target : DlqStatus.values()) {
            assertThat(terminalStatus.canTransitionTo(target))
                    .as("%s should not be able to transition to %s", terminalStatus, target)
                    .isFalse();
        }
    }
}
