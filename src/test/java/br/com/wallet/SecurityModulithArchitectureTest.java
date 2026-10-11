package br.com.wallet;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Security Architecture & Modulith Isolation Tests (I-SEC-013, I-SEC-014, I-SEC-015, TASK-10.16)")
class SecurityModulithArchitectureTest {

    private final JavaClasses allProductionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("br.com.wallet");

    @Test
    @DisplayName("I-SEC-013: Spring Modulith module verification passes with security module")
    void verifyModulithArchitecture() {
        ApplicationModules modules = ApplicationModules.of(WalletApplication.class);
        modules.verify();
        assertThat(modules.getModuleByName("security")).isPresent();
    }

    @Test
    @DisplayName("I-SEC-014: Transactional Ledger domain must have zero dependencies on Security contracts or adapters")
    void ledgerMustNotDependOnSecurity() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("br.com.wallet.ledger..")
                .should().dependOnClassesThat().resideInAPackage("br.com.wallet.security..")
                .allowEmptyShould(true);

        rule.check(allProductionClasses);
    }

    @Test
    @DisplayName("I-SEC-015: Non-security core packages must not depend directly on javax.crypto primitives")
    void nonSecurityPackagesMustNotImportJavaxCrypto() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage(
                        "br.com.wallet.ledger..",
                        "br.com.wallet.fraud..",
                        "br.com.wallet.savings..",
                        "br.com.wallet.goals..",
                        "br.com.wallet.dlq..",
                        "br.com.wallet.core..",
                        "br.com.wallet.infrastructure.."
                )
                .should().dependOnClassesThat().resideInAPackage("javax.crypto..")
                .allowEmptyShould(true);

        rule.check(allProductionClasses);
    }

    @Test
    @DisplayName("I-SEC-013: Security module must be pure Java and have zero dependencies on frameworks, databases, or web")
    void securityModuleMustNotDependOnFrameworksOrDatabases() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("br.com.wallet.security..")
                .and().haveSimpleNameNotEndingWith("package-info")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "com.github.benmanes.caffeine..",
                        "io.lettuce..",
                        "org.redisson..",
                        "com.fasterxml.jackson..",
                        "java.sql..",
                        "javax.sql.."
                )
                .allowEmptyShould(true);

        rule.check(allProductionClasses);
    }

    @Test
    @DisplayName("Dual Boundary: Edge security internal adapters must not leak into core domain packages")
    void edgeInternalSecurityMustNotBeImportedByCoreDomains() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage(
                        "br.com.wallet.ledger..",
                        "br.com.wallet.fraud..",
                        "br.com.wallet.savings..",
                        "br.com.wallet.goals..",
                        "br.com.wallet.dlq..",
                        "br.com.wallet.core.."
                )
                .should().dependOnClassesThat().resideInAPackage("br.com.wallet.edge.internal.security..")
                .allowEmptyShould(true);

        rule.check(allProductionClasses);
    }
}
