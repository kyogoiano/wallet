package br.com.wallet;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@DisplayName("Architectural Negative Verification: Internal Listeners Zero-NATS & Zero-DLQ (REQ-STRM-008, I-STREAM-004, I-STREAM-007)")
class NoInternalEventNatsDependencyTest {

    private final JavaClasses allProductionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("br.com.wallet");

    @Test
    @DisplayName("I-STREAM-004: In-process event listeners must have zero dependencies on NATS classes")
    void inProcessListenersMustNotDependOnNats() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..internal.listener..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "io.nats..",
                        "br.com.wallet.infrastructure.messaging.consumer.."
                );

        rule.check(allProductionClasses);
    }

    @Test
    @DisplayName("I-STREAM-008: In-process event listeners must not depend on Outbox Relay worker")
    void inProcessListenersMustNotDependOnOutboxRelay() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..internal.listener..")
                .should().dependOnClassesThat().haveFullyQualifiedName(
                        "br.com.wallet.ledger.internal.outbox.OutboxRelayWorker"
                );

        rule.check(allProductionClasses);
    }

    @Test
    @DisplayName("REQ-INTEL-007, I-INTEL-002: Intelligence module must have zero dependencies on NATS or Outbox Relay")
    void intelligenceModuleMustNotDependOnNatsOrOutboxRelay() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("br.com.wallet.intelligence..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "io.nats..",
                        "br.com.wallet.infrastructure.messaging..",
                        "br.com.wallet.ledger.internal.outbox.."
                );

        rule.check(allProductionClasses);
    }
}
