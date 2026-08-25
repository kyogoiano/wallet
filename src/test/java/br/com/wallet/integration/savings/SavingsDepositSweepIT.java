package br.com.wallet.integration.savings;

import br.com.wallet.core.context.OperationOrigin;
import br.com.wallet.infrastructure.messaging.publisher.NatsEventPublisher;
import br.com.wallet.ledger.api.BalanceUseCase;
import br.com.wallet.ledger.api.CreateWalletUseCase;
import br.com.wallet.ledger.api.DepositFundsUseCase;
import br.com.wallet.ledger.api.context.Deposit;
import br.com.wallet.ledger.internal.outbox.OutboxRelay;
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
@DisplayName("Savings Deposit Sweep Integration Tests")
public class SavingsDepositSweepIT extends DockerProperties {

    @Autowired
    private DepositFundsUseCase depositFundsUseCase;

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

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private NatsEventPublisher natsEventPublisher;

    private UUID primaryWalletId;
    private UUID savingsWalletId;
    private UUID userId;

    @BeforeEach
    void setup() {
        cleaner.clean();
        primaryWalletId = UUID.randomUUID();
        savingsWalletId = UUID.randomUUID();
        userId = UUID.randomUUID();

        createWalletUseCase.handle(primaryWalletId, userId);
        createWalletUseCase.handle(savingsWalletId, userId);
    }

    @Test
    @DisplayName("Should automatically execute percentage sweep upon deposit")
    void shouldExecutePercentageSweepOnDeposit() {
        // Given active savings plan: 10% on primaryWallet -> savingsWallet
        savingsPlanUseCase.createPlan(new CreateSavingsPlanCommand(
                primaryWalletId,
                savingsWalletId,
                BigDecimal.ZERO,
                List.of(new CreateSavingsRuleCommand(SavingsRuleType.PERCENTAGE, null, new BigDecimal("10.00"), null))
        ));

        // When: User deposits R$ 5,000.00
        depositFundsUseCase.handle(new Deposit(
                primaryWalletId, userId, new BigDecimal("5000.00"), UUID.randomUUID(), OperationOrigin.USER
        ));

        await().atMost(Duration.ofSeconds(1)).pollInterval(Duration.ofMillis(50))
                .untilAsserted(() -> {

                    // Then: Primary balance should be 4,500.00 (5,000 - 500 saved), Savings balance should be 500.00
                    assertThat(balanceUseCase.getBalance(primaryWalletId))
                            .isEqualByComparingTo("4500.00");

                    assertThat(balanceUseCase.getBalance(savingsWalletId))
                            .isEqualByComparingTo("500.00");

                    SavingsMetricsResponse metrics =
                            savingsQueryUseCase.getMetrics(primaryWalletId);

                    assertThat(metrics.totalSaved())
                            .isEqualByComparingTo("500.00");

                    assertThat(metrics.executionCount())
                            .isEqualTo(1L);
                });
    }
}
