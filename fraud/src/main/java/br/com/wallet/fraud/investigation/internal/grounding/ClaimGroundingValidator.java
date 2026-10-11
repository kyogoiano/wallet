package br.com.wallet.fraud.investigation.internal.grounding;

import br.com.wallet.fraud.investigation.api.model.AtomicEvidenceItem;
import br.com.wallet.fraud.investigation.api.model.InvestigationClaim;
import br.com.wallet.fraud.investigation.api.model.InvestigationEvidence;
import br.com.wallet.fraud.investigation.api.model.InvestigationNarrative;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates that every claim in the generated narrative is traceable to atomic evidence items (I-VEC-006).
 */
@Component
public class ClaimGroundingValidator {

    public boolean isValid(
        @NonNull final InvestigationNarrative narrative,
        @NonNull final InvestigationEvidence evidence
    ) {
        Objects.requireNonNull(narrative, "narrative cannot be null");
        Objects.requireNonNull(evidence, "evidence cannot be null");

        if (narrative.claims().isEmpty()) {
            return false;
        }

        Set<String> validEvidenceIds = evidence.evidenceItems().stream()
            .map(AtomicEvidenceItem::id)
            .collect(Collectors.toSet());

        for (InvestigationClaim claim : narrative.claims()) {
            if (claim.summary().isBlank()) {
                return false;
            }
            if (claim.evidenceReferences().isEmpty()) {
                return false;
            }
            for (String refId : claim.evidenceReferences()) {
                if (!validEvidenceIds.contains(refId)) {
                    // Claim references a hallucinated or missing evidence ID!
                    return false;
                }
            }
        }

        return true;
    }
}
