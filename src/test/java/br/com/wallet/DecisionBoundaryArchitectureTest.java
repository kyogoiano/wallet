package br.com.wallet;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@DisplayName("Decision Boundary & Architectural Airgap Tests (I-TYPED-001, I-TYPED-002, REQ-TYPED-007, REQ-TYPED-010)")
class DecisionBoundaryArchitectureTest {

    private final JavaClasses allProductionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("br.com.wallet");

    @Test
    @DisplayName("I-TYPED-001 & REQ-TYPED-007: Transactional ledger must not depend on fraud decision algebra")
    void ledgerMustNotDependOnDecisionAlgebra() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("br.com.wallet.ledger..")
                .should().dependOnClassesThat().resideInAPackage("br.com.wallet.decision..")
                .allowEmptyShould(true);

        rule.check(allProductionClasses);
    }

    @Test
    @DisplayName("I-TYPED-001 & I-TYPED-002: Hot-path FraudGate and Fusion must not depend on nearline decision algebra")
    void fraudGateMustNotDependOnDecisionAlgebra() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage(
                        "br.com.wallet.fraud.fusion.internal.gate..",
                        "br.com.wallet.fraud.fusion.api..",
                        "br.com.wallet.fraud.rules.."
                )
                .should().dependOnClassesThat().resideInAPackage("br.com.wallet.decision..")
                .allowEmptyShould(true);

        rule.check(allProductionClasses);
    }

    @Test
    @DisplayName("REQ-TYPED-010: Decision algebra must be pure Java and not depend on spring-ai or third-party AI frameworks")
    void decisionAlgebraMustNotDependOnExternalAiFrameworks() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("br.com.wallet.decision..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.ai..",
                        "dev.langchain4j..",
                        "com.theokanning.openai.."
                )
                .allowEmptyShould(true);

        rule.check(allProductionClasses);
    }

    @Test
    @DisplayName("Clean Architecture: Decision algebra must not depend on transactional ledger")
    void decisionAlgebraMustNotDependOnLedger() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("br.com.wallet.decision..")
                .should().dependOnClassesThat().resideInAPackage("br.com.wallet.ledger..")
                .allowEmptyShould(true);

        rule.check(allProductionClasses);
    }

    @Test
    @DisplayName("RULE-CAP-007: Infrastructure adapters must not depend directly on decision algebra")
    void infrastructureMustNotDependOnDecisionAlgebra() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("br.com.wallet.infrastructure..")
                .should().dependOnClassesThat().resideInAPackage("br.com.wallet.decision..")
                .allowEmptyShould(true);

        rule.check(allProductionClasses);
    }
}
