package br.com.wallet.unit.fraud.investigation;

import br.com.wallet.fraud.investigation.api.model.AtomicEvidenceItem;
import br.com.wallet.fraud.investigation.api.model.FraudRiskSnapshot;
import br.com.wallet.fraud.investigation.api.model.InvestigationEvidence;
import br.com.wallet.fraud.investigation.api.model.RecommendedAction;
import br.com.wallet.fraud.investigation.api.model.RiskClassification;
import br.com.wallet.fraud.investigation.internal.sanitization.PiiMaskingService;
import br.com.wallet.fraud.investigation.internal.sanitization.SanitizedInferenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PiiMaskingService Unit Tests (Hard Architectural Boundary for PII Anonymization)")
class PiiMaskingServiceTest {

    private PiiMaskingService maskingService;

    @BeforeEach
    void setUp() {
        maskingService = new PiiMaskingService();
    }

    @Test
    @DisplayName("REQ-VEC-004: Should sanitize target ID, counterparties, and sensitive identifiers")
    void shouldSanitizeSensitiveData() {
        UUID targetId = UUID.randomUUID();
        UUID counterpartyId = UUID.randomUUID();

        AtomicEvidenceItem item = new AtomicEvidenceItem(
            "EVID-001",
            "TRANSFER",
            targetId.toString(),
            Map.of(
                "dest", counterpartyId.toString(),
                "cpf", "12345678901",
                "email", "fraudster@suspicious.com",
                "amount", 5000.0
            )
        );

        InvestigationEvidence evidence = new InvestigationEvidence(
            FraudRiskSnapshot.empty(),
            List.of(item)
        );

        SanitizedInferenceContext sanitized = maskingService.sanitize(
            targetId,
            evidence,
            RiskClassification.HIGH,
            List.of(RecommendedAction.MANUAL_REVIEW)
        );

        assertThat(sanitized.maskedTargetId()).isEqualTo(PiiMaskingService.TARGET_SURROGATE);
        AtomicEvidenceItem sanitizedItem = sanitized.sanitizedEvidenceItems().get(0);

        assertThat(sanitizedItem.subject()).isEqualTo(PiiMaskingService.TARGET_SURROGATE);
        assertThat(sanitizedItem.facts().get("dest").toString()).startsWith("COUNTERPARTY_");
        assertThat(sanitizedItem.facts().get("cpf")).isEqualTo("ANONYMIZED_IDENTIFIER");
        assertThat(sanitizedItem.facts().get("email")).isEqualTo("ANONYMIZED_IDENTIFIER");
        assertThat(sanitizedItem.facts().get("amount")).isEqualTo(5000.0);
    }
}
