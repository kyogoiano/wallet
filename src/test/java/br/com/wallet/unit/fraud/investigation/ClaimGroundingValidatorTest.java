package br.com.wallet.unit.fraud.investigation;

import br.com.wallet.fraud.investigation.api.model.AtomicEvidenceItem;
import br.com.wallet.fraud.investigation.api.model.ClaimType;
import br.com.wallet.fraud.investigation.api.model.FraudRiskSnapshot;
import br.com.wallet.fraud.investigation.api.model.InvestigationClaim;
import br.com.wallet.fraud.investigation.api.model.InvestigationEvidence;
import br.com.wallet.fraud.investigation.api.model.InvestigationNarrative;
import br.com.wallet.fraud.investigation.internal.grounding.ClaimGroundingValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ClaimGroundingValidator Unit Tests (Zero Hallucination & Fact Grounding I-VEC-006)")
class ClaimGroundingValidatorTest {

    private ClaimGroundingValidator validator;
    private InvestigationEvidence evidence;

    @BeforeEach
    void setUp() {
        validator = new ClaimGroundingValidator();
        evidence = new InvestigationEvidence(
            FraudRiskSnapshot.empty(),
            List.of(
                new AtomicEvidenceItem("GRAPH-001", "DEVICE_CLUSTER", "USER", Map.of("sharedCount", 4)),
                new AtomicEvidenceItem("TEMPORAL-014", "RAPID_DRAIN", "USER", Map.of("windowHours", 2))
            )
        );
    }

    @Test
    @DisplayName("I-VEC-006: Should pass validation when all claims cite valid evidence IDs")
    void shouldPassWhenAllClaimsAreGrounded() {
        InvestigationNarrative narrative = new InvestigationNarrative(
            "Account shows suspicious mule characteristics.",
            List.of(
                new InvestigationClaim(ClaimType.SHARED_INFRASTRUCTURE, "Shared device clustering detected.", List.of("GRAPH-001")),
                new InvestigationClaim(ClaimType.RAPID_FUND_MOVEMENT, "Pass-through drainage detected within 2h.", List.of("TEMPORAL-014"))
            ),
            "Temporary restrictions recommended."
        );

        boolean valid = validator.isValid(narrative, evidence);
        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("I-VEC-006: Should reject narrative citing hallucinated evidence ID")
    void shouldRejectHallucinatedEvidenceId() {
        InvestigationNarrative narrative = new InvestigationNarrative(
            "Account shows suspicious mule characteristics.",
            List.of(
                new InvestigationClaim(ClaimType.SHARED_INFRASTRUCTURE, "Fabricated evidence assertion.", List.of("GHOST-999"))
            ),
            "Restrictions recommended."
        );

        boolean valid = validator.isValid(narrative, evidence);
        assertThat(valid).isFalse();
    }
}
