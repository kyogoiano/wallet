package br.com.wallet.integration.savings;

import br.com.wallet.core.context.OperationOrigin;
import br.com.wallet.ledger.api.BalanceUseCase;
import br.com.wallet.ledger.api.CreateWalletUseCase;
import br.com.wallet.ledger.api.DepositFundsUseCase;
import br.com.wallet.ledger.api.TransferFundsUseCase;
import br.com.wallet.ledger.api.context.Deposit;
import br.com.wallet.ledger.api.context.Transfer;
import br.com.wallet.savings.api.SavingsPlanUseCase;
import br.com.wallet.savings.api.SavingsQueryUseCase;
import br.com.wallet.savings.api.dto.CreateSavingsPlanCommand;
import br.com.wallet.savings.api.dto.CreateSavingsRuleCommand;
import br.com.wallet.savings.api.dto.SavingsMetricsResponse;
import br.com.wallet.savings.api.model.SavingsRuleType;
import br.com.wallet.support.DatabaseCleaner;
import br.com.wallet.support.DockerProperties;
import br.com.wallet.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@DisplayName("Round-Up Micro-Savings & Loop Prevention Integration Tests")
public class RoundUpMicroSavingsIT extends DockerProperties {

    @Autowired
    private DepositFundsUseCase depositFundsUseCase;

    @Autowired
    private TransferFundsUseCase transferFundsUseCase;

    @Autowired
    private CreateWalletUseCase createWalletUseCase;

    @Autowired
    private BalanceUseCase balanceUseCase;

    @Autowired
    private SavingsPlanUseCase savingsPlanUseCase;

    @Autowired
    private SavingsQueryUseCase savingsQueryUseCase;

    @Autowired
    private DatabaseCleaner cleaner;

    private UUID primaryWalletId;
    private UUID merchantWalletId;
    private UUID savingsWalletId;
    private UUID userId;

    @BeforeEach
    void setup() {
        cleaner.clean();
        primaryWalletId = UUID.randomUUID();
        merchantWalletId = UUID.randomUUID();
        savingsWalletId = UUID.randomUUID();
        userId = UUID.randomUUID();

        createWalletUseCase.handle(primaryWalletId, userId);
        createWalletUseCase.handle(merchantWalletId, UUID.randomUUID());
        createWalletUseCase.handle(savingsWalletId, userId);

        // Seed initial balance in primary wallet: R$ 500.00 (system seed)
        depositFundsUseCase.handle(new Deposit(
                primaryWalletId, userId, new BigDecimal("500.00"), UUID.randomUUID(), OperationOrigin.SYSTEM
        ));
    }

    @Test
    @DisplayName("Should execute round-up micro-savings and prevent infinite recursive loops (I-SAVINGS-001)")
    void shouldExecuteRoundUpAndPreventInfiniteLoops() {
        // Given active Round-Up plan (step = 5.00)
        savingsPlanUseCase.createPlan(new CreateSavingsPlanCommand(
                primaryWalletId,
                savingsWalletId,
                BigDecimal.ZERO,
                List.of(new CreateSavingsRuleCommand(SavingsRuleType.ROUND_UP, new BigDecimal("5.00"), null, null))
        ));

        // When: User spends R$ 47.30 to merchant
        transferFundsUseCase.handle(new Transfer(
                primaryWalletId, merchantWalletId, new BigDecimal("47.30"), UUID.randomUUID(), OperationOrigin.USER
        ));
        await().atMost(Duration.ofSeconds(1)).pollInterval(Duration.ofMillis(50))
                .untilAsserted(() -> {
                    // Then:
                    // 1. Merchant received 47.30
                    assertThat(balanceUseCase.getBalance(merchantWalletId)).isEqualByComparingTo("47.30");

                    // 2. Savings wallet received round-up delta of 2.70 (ceil(47.30/5)*5 - 47.30)
                    assertThat(balanceUseCase.getBalance(savingsWalletId)).isEqualByComparingTo("2.70");

                    // 3. Primary wallet balance: 500 - 47.30 - 2.70 = 450.00
                    assertThat(balanceUseCase.getBalance(primaryWalletId)).isEqualByComparingTo("450.00");

                    // 4. Metrics verify exactly 1 execution (no recursive loop triggered by the 2.70 transfer!)
                    SavingsMetricsResponse metrics = savingsQueryUseCase.getMetrics(primaryWalletId);
                    assertThat(metrics.totalSaved()).isEqualByComparingTo("2.70");
                    assertThat(metrics.executionCount()).isEqualTo(1L);
        });

    }
}
