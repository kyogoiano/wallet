package br.com.wallet.fraud.investigation.api.model;

import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Objects;

/**
 * An atomic, deterministic evidence item with unique identifier, category type, and factual attributes.
 */
public record AtomicEvidenceItem(
    @NonNull String id,
    @NonNull String type,
    @NonNull String subject,
    @NonNull Map<String, Object> facts
) {
    public AtomicEvidenceItem {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(subject, "subject cannot be null");
        Objects.requireNonNull(facts, "facts cannot be null");
        facts = Map.copyOf(facts);
    }
}
