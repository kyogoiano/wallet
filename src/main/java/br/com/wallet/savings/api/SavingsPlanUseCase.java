package br.com.wallet.savings.api;

import br.com.wallet.savings.api.dto.CreateSavingsPlanCommand;
import br.com.wallet.savings.api.dto.CreateSavingsRuleCommand;
import br.com.wallet.savings.api.model.SavingsPlanDto;
import br.com.wallet.savings.api.model.SavingsRuleDto;
import java.util.List;
import java.util.UUID;

public interface SavingsPlanUseCase {
    SavingsPlanDto createPlan(CreateSavingsPlanCommand command);
    SavingsPlanDto getPlan(UUID planId);
    List<SavingsPlanDto> getPlansForWallet(UUID walletId);
    void pausePlan(UUID planId);
    void resumePlan(UUID planId);
    void deletePlan(UUID planId);
    SavingsRuleDto addRule(UUID planId, CreateSavingsRuleCommand command);
    void removeRule(UUID ruleId);
    void toggleRule(UUID ruleId, boolean isActive);
    List<SavingsRuleDto> getRulesForPlan(UUID planId);
}
