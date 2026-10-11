package br.com.wallet.support;

import br.com.wallet.fraud.investigation.api.model.ClaimType;
import br.com.wallet.fraud.investigation.api.model.InvestigationClaim;
import br.com.wallet.fraud.investigation.api.model.InvestigationNarrative;
import br.com.wallet.fraud.investigation.spi.LocalInferenceClient;
import br.com.wallet.fraud.investigation.spi.StructuredInferenceRequest;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic, zero-Docker fake inference client for Tier 1 unit tests (History 30).
 */
public class FakeInferenceClient implements LocalInferenceClient {

    private boolean failWithEmpty = false;
    private InvestigationNarrative customNarrative = null;

    public FakeInferenceClient() {}

    public FakeInferenceClient(boolean failWithEmpty) {
        this.failWithEmpty = failWithEmpty;
    }

    public void setFailWithEmpty(boolean failWithEmpty) {
        this.failWithEmpty = failWithEmpty;
    }

    public void setCustomNarrative(InvestigationNarrative narrative) {
        this.customNarrative = narrative;
    }

    @Override
    @NonNull
    public Optional<InvestigationNarrative> generateNarrative(@NonNull final StructuredInferenceRequest request) {
        Objects.requireNonNull(request, "request cannot be null");
        if (failWithEmpty) {
            return Optional.empty();
        }

        if (customNarrative != null) {
            return Optional.of(customNarrative);
        }

        String refId = request.context().sanitizedEvidenceItems().isEmpty()
            ? "REF-001"
            : request.context().sanitizedEvidenceItems().getFirst().id();

        InvestigationNarrative defaultNarrative = new InvestigationNarrative(
            "Account shows behavioral indicators consistent with monitored risk thresholds.",
            List.of(new InvestigationClaim(
                ClaimType.ARCHETYPE_SIMILARITY,
                "Observed pass-through transactions and velocity patterns match designated archetype.",
                List.of(refId)
            )),
            "Temporary restrictions recommended pending investigation review."
        );

        return Optional.of(defaultNarrative);
    }
}
