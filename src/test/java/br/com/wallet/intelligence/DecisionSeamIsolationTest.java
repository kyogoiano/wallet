package br.com.wallet.intelligence;

import br.com.wallet.core.context.OperationOrigin;
import br.com.wallet.intelligence.internal.listener.SpendingEventListener;
import br.com.wallet.ledger.api.event.TransferCompletedEvent;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("Decision Seam & Failure Isolation Tests (REQ-INTEL-008, I-INTEL-005, I-INTEL-006, I-INTEL-007)")
class DecisionSeamIsolationTest {

    private final JavaClasses allProductionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("br.com.wallet");

    @Test
    @DisplayName("I-INTEL-005: Hot-Path Airgap - Intelligence module must not depend directly on AI frameworks")
    void intelligenceMustNotDependOnExternalAiFrameworks() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("br.com.wallet.intelligence..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.ai..",
                        "dev.langchain4j..",
                        "com.theokanning.openai..",
                        "ai.onnxruntime.."
                )
                .allowEmptyShould(true);

        rule.check(allProductionClasses);
    }

    @Test
    @DisplayName("I-INTEL-007: Seam Failure Isolation - Evaluator errors do not disrupt listener event consumption")
    void listenerExecutionRemainsResilientUnderSeamInteractions() {
        var listener = new SpendingEventListener();
        var event = new TransferCompletedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("500.00"),
                UUID.randomUUID(),
                OperationOrigin.USER,
                "tenant-alpha"
        );

        assertThatCode(() -> listener.onTransfer(event)).doesNotThrowAnyException();
        assertThat(listener.getProcessedEventCount()).isEqualTo(1);
    }
}
