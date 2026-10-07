package br.com.wallet.intelligence;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@DisplayName("Zero Ledger Mutation Architectural Verification (REQ-INTEL-007, I-INTEL-001, I-INTEL-004)")
class ZeroLedgerMutationTest {

    private final JavaClasses allProductionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("br.com.wallet");

    @Test
    @DisplayName("I-INTEL-004: Intelligence classes must not access ledger internal packages or persistence")
    void intelligenceMustNotAccessLedgerInternals() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("br.com.wallet.intelligence..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "br.com.wallet.ledger.internal..",
                        "br.com.wallet.ledger.infrastructure.persistence..",
                        "br.com.wallet.ledger.infrastructure..",
                        "br.com.wallet.ledger.service.."
                );

        rule.check(allProductionClasses);
    }

    @Test
    @DisplayName("I-INTEL-001: Intelligence classes must not depend on ledger entities or mutating DAOs")
    void intelligenceMustNotDependOnLedgerDaosOrEntities() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("br.com.wallet.intelligence..")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Dao")
                .andShould().dependOnClassesThat().resideInAPackage("..ledger..");

        rule.check(allProductionClasses);
    }
}
