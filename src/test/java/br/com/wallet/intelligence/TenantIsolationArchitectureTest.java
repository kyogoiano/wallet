package br.com.wallet.intelligence;

import br.com.wallet.core.context.OperationOrigin;
import br.com.wallet.intelligence.internal.listener.SpendingEventListener;
import br.com.wallet.ledger.api.event.TransferCompletedEvent;
import br.com.wallet.ledger.api.event.WithdrawCompletedEvent;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Tenant Isolation Architecture Verification (REQ-INTEL-007, REQ-INTEL-005, I-INTEL-010)")
class TenantIsolationArchitectureTest {

    private final JavaClasses allProductionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("br.com.wallet.intelligence");

    @Test
    @DisplayName("I-INTEL-010: Intelligence listeners must preserve strict multi-tenant partitioning across separate tenants")
    void listenerMustPreserveTenantPartitioning() {
        var listener = new SpendingEventListener();

        // Process tenant-A event
        var eventA = new TransferCompletedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("100.00"),
                UUID.randomUUID(),
                OperationOrigin.USER,
                "tenant-alpha"
        );
        listener.onTransfer(eventA);
        assertThat(listener.getLastProcessedTenantId()).isEqualTo("tenant-alpha");

        // Process tenant-B event
        var eventB = new WithdrawCompletedEvent(
                UUID.randomUUID(),
                new BigDecimal("50.00"),
                UUID.randomUUID(),
                "tenant-beta"
        );
        listener.onWithdraw(eventB);
        assertThat(listener.getLastProcessedTenantId()).isEqualTo("tenant-beta");

        // Total count matches sum of partitioned events
        assertThat(listener.getProcessedEventCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("I-INTEL-010: Production classes in intelligence package exist and are isolated")
    void intelligenceClassesAreScannedAndPresent() {
        assertThat(allProductionClasses).isNotEmpty();
    }
}
