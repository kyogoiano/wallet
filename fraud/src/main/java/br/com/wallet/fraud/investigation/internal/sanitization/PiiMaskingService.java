package br.com.wallet.fraud.investigation.internal.sanitization;

import br.com.wallet.fraud.investigation.api.model.AtomicEvidenceItem;
import br.com.wallet.fraud.investigation.api.model.InvestigationEvidence;
import br.com.wallet.fraud.investigation.api.model.RecommendedAction;
import br.com.wallet.fraud.investigation.api.model.RiskClassification;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Hard architectural boundary ensuring no raw PII or sensitive database identifiers
 * are passed to the inference models (I-VEC-004 & REQ-VEC-004).
 */
@Component
public class PiiMaskingService {

    public static final String TARGET_SURROGATE = "MASK_USER_TARGET";

    @NonNull
    public SanitizedInferenceContext sanitize(
        @NonNull final UUID targetEntityId,
        @NonNull final InvestigationEvidence evidence,
        @NonNull final RiskClassification classification,
        @NonNull final List<RecommendedAction> allowedActions
    ) {
        Objects.requireNonNull(targetEntityId, "targetEntityId cannot be null");
        Objects.requireNonNull(evidence, "evidence cannot be null");
        Objects.requireNonNull(classification, "classification cannot be null");
        Objects.requireNonNull(allowedActions, "allowedActions cannot be null");

        Map<String, String> tokenMap = new HashMap<>();
        tokenMap.put(targetEntityId.toString(), TARGET_SURROGATE);

        List<AtomicEvidenceItem> sanitizedItems = new ArrayList<>();
        int counterpartyIndex = 1;

        for (AtomicEvidenceItem item : evidence.evidenceItems()) {
            String subject = item.subject();
            String maskedSubject = tokenMap.computeIfAbsent(subject, k -> {
                if (k.equals(targetEntityId.toString())) {
                    return TARGET_SURROGATE;
                }
                return "COUNTERPARTY_" + (tokenMap.size());
            });

            Map<String, Object> sanitizedFacts = new HashMap<>();
            for (Map.Entry<String, Object> fact : item.facts().entrySet()) {
                Object val = fact.getValue();
                if (val instanceof String strVal) {
                    if (strVal.matches("^[0-9a-fA-F-]{36}$")) {
                        // Mask UUID
                        sanitizedFacts.put(fact.getKey(), tokenMap.computeIfAbsent(strVal, k -> "COUNTERPARTY_" + tokenMap.size()));
                    } else if (strVal.matches("^\\d{11}$") || strVal.contains("@")) {
                        // Mask CPF or Email
                        sanitizedFacts.put(fact.getKey(), "ANONYMIZED_IDENTIFIER");
                    } else {
                        sanitizedFacts.put(fact.getKey(), strVal);
                    }
                } else {
                    sanitizedFacts.put(fact.getKey(), val);
                }
            }

            sanitizedItems.add(new AtomicEvidenceItem(item.id(), item.type(), maskedSubject, sanitizedFacts));
        }

        return new SanitizedInferenceContext(
            TARGET_SURROGATE,
            evidence.risks(),
            sanitizedItems,
            classification,
            allowedActions
        );
    }
}
