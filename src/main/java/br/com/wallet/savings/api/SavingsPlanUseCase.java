package br.com.wallet.savings.api;

import br.com.wallet.savings.api.dto.CreateSavingsPlanCommand;
import br.com.wallet.savings.api.model.SavingsPlanDto;
import java.util.List;
import java.util.UUID;

public interface SavingsPlanUseCase {
    SavingsPlanDto createPlan(CreateSavingsPlanCommand command);
    void pausePlan(UUID planId);
    void resumePlan(UUID planId);
    void deletePlan(UUID planId);
    List<SavingsPlanDto> getPlansForWallet(UUID walletId);
}
