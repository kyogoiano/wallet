package br.com.wallet.integration.infrastructure;

import br.com.wallet.ledger.internal.persistence.AccountDao;
import br.com.wallet.ledger.internal.persistence.LedgerDao;
import br.com.wallet.ledger.internal.persistence.WalletOperationsDao;
import br.com.wallet.ledger.internal.service.TransferFundsService;
import br.com.wallet.ledger.internal.service.WalletOperationService;
import br.com.wallet.support.DatabaseCleaner;
import br.com.wallet.support.DockerProperties;
import br.com.wallet.support.IntegrationTestBase;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@DisplayName("Dragonfly Architectural Boundary Verification Test (REQ-DF20-014, I-LEDGER-001)")
public class DragonflyArchitecturalBoundaryTest extends DockerProperties {

    @MockitoSpyBean
    private RedisCommands<String, String> redisCommands;

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private LedgerDao ledgerDao;

    @Autowired
    private DatabaseCleaner cleaner;

    @BeforeEach
    void setUp() {
        cleaner.clean();
    }

    @Test
    @DisplayName("REQ-DF20-014: Static ArchUnit Check - ledger package must never depend on Redis/Dragonfly infrastructure")
    void ledgerPackageMustNotDependOnRedisInfrastructure() {
        JavaClasses ledgerClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("br.com.wallet.ledger");

        ArchRule rule = noClasses()
                .that().resideInAPackage("br.com.wallet.ledger..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "io.lettuce..",
                        "org.springframework.data.redis..",
                        "br.com.wallet.infrastructure.config.RedisConfig",
                        "br.com.wallet.infrastructure.config.RedisScripts"
                );

        rule.check(ledgerClasses);
    }

    @Test
    @DisplayName("I-LEDGER-001: Balance and Ledger Source of Truth is strictly PostgreSQL, never Dragonfly")
    void balanceAndLedgerMustRelyExclusivelyOnRelationalPersistence() {
        // Verify AccountDao and LedgerDao rely exclusively on SQL/PostgreSQL persistence
        assertThat(accountDao).isNotNull();
        assertThat(ledgerDao).isNotNull();

        // ArchUnit assertion: Dao implementations must reside in persistence package and have zero Redis dependencies
        JavaClasses daoClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("br.com.wallet.ledger.internal.persistence");

        ArchRule rule = noClasses()
                .that().resideInAPackage("br.com.wallet.ledger.internal.persistence..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "io.lettuce..",
                        "org.springframework.data.redis.."
                );

        rule.check(daoClasses);
    }
}
