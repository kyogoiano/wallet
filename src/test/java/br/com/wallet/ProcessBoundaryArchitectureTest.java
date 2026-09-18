package br.com.wallet;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@DisplayName("Process Boundary Architecture Test (I-CONTRACT-001, REQ-PRC-005 & TASK-PRC-2.2)")
class ProcessBoundaryArchitectureTest {

    private final JavaClasses allProductionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("br.com.wallet");

    @Test
    @DisplayName("REQ-PRC-005: Edge must not depend on Core domain packages (ledger, fraud, savings, goals)")
    void edgeMustNotDependOnCoreDomainPackages() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("br.com.wallet.edge..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "br.com.wallet.ledger..",
                        "br.com.wallet.fraud..",
                        "br.com.wallet.savings..",
                        "br.com.wallet.goals..",
                        "br.com.wallet.dlq.."
                );

        rule.check(allProductionClasses);
    }

    @Test
    @DisplayName("REQ-PRC-003 & I-STATE-001: Edge must not depend on relational persistence or SQL packages")
    void edgeMustNotDependOnSqlOrPersistence() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("br.com.wallet.edge..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "javax.sql..",
                        "jakarta.persistence..",
                        "org.springframework.jdbc..",
                        "org.springframework.data.jpa..",
                        "org.hibernate.."
                );

        rule.check(allProductionClasses);
    }

    @Test
    @DisplayName("TASK-PRC-2.2: Edge must depend only on published contracts and not on Core infrastructure/implementation")
    void assertEdgeDependsOnlyOnPublishedContracts() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("br.com.wallet.edge..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "br.com.wallet.infrastructure..",
                        "br.com.wallet.ledger..",
                        "br.com.wallet.fraud..",
                        "br.com.wallet.savings..",
                        "br.com.wallet.goals..",
                        "br.com.wallet.dlq.."
                );

        rule.check(allProductionClasses);
    }

    @Test
    @DisplayName("TASK-PRC-2.2: Core domain modules must never depend on internal Edge implementation classes")
    void assertCoreDoesNotExposeEdgeImplementation() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage(
                        "br.com.wallet.ledger..",
                        "br.com.wallet.fraud..",
                        "br.com.wallet.savings..",
                        "br.com.wallet.goals..",
                        "br.com.wallet.dlq.."
                )
                .should().dependOnClassesThat().resideInAPackage("br.com.wallet.edge.internal..");

        rule.check(allProductionClasses);
    }

    @Test
    @DisplayName("REQ-TOP-015 & I-PLATFORM-001: Application packages must not depend on Kubernetes or container orchestrator SDKs")
    void assertZeroKubernetesOrPlatformDependencies() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("br.com.wallet..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "io.fabric8..",
                        "io.kubernetes..",
                        "com.github.dockerjava..",
                        "org.mandas.docker.."
                );

        rule.check(allProductionClasses);
    }
}
