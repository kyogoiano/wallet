package br.com.wallet.copilot;

import br.com.wallet.copilot.internal.dao.ProposalDao;
import br.com.wallet.copilot.internal.sweeper.ProposalTtlSweeper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProposalTtlSweeper Unit Tests (TASK-4.13, REQ-COPILOT-012)")
class ProposalTtlSweeperTest {

    @Mock
    private ProposalDao proposalDao;

    @Test
    @DisplayName("REQ-COPILOT-012: Sweeper marks overdue proposals as EXPIRED")
    void shouldSweepExpiredProposals() {
        when(proposalDao.expireOverdueProposals(any(Instant.class))).thenReturn(7);

        ProposalTtlSweeper sweeper = new ProposalTtlSweeper(proposalDao);
        int expired = sweeper.sweepExpiredProposals();

        assertThat(expired).isEqualTo(7);
        verify(proposalDao).expireOverdueProposals(any(Instant.class));
    }
}
