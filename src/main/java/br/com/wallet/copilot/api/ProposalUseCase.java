package br.com.wallet.copilot.api;

import br.com.wallet.copilot.api.dto.ApproveProposalCommand;
import br.com.wallet.copilot.api.dto.CreateProposalCommand;
import br.com.wallet.copilot.api.dto.ProposalResponse;
import br.com.wallet.copilot.api.dto.RejectProposalCommand;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProposalUseCase {

    @NonNull
    ProposalResponse createProposal(@NonNull String tenantId, @NonNull String createdBy, @NonNull CreateProposalCommand command);

    @NonNull
    ProposalResponse approveProposal(@NonNull String tenantId, @NonNull String approvedBy, @NonNull ApproveProposalCommand command);

    @NonNull
    ProposalResponse rejectProposal(@NonNull String tenantId, @NonNull String rejectedBy, @NonNull RejectProposalCommand command);

    @NonNull
    Optional<ProposalResponse> findById(@NonNull UUID proposalId, @NonNull String tenantId);

    @NonNull
    List<ProposalResponse> listPending(@NonNull String tenantId, @NonNull UUID walletId);
}
