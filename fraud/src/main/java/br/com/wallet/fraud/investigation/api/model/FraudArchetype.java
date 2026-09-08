package br.com.wallet.fraud.investigation.api.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Closed taxonomy of behavioral fraud archetypes identified by the vector matching engine (SPEC-000.7).
 */
public enum FraudArchetype {
    NONE,
    MONEY_MULE_RAPID_DRAIN,
    SMURFING,
    ACCOUNT_TAKEOVER;

    @NonNull
    public static FraudArchetype fromString(@Nullable final String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        for (FraudArchetype archetype : values()) {
            if (archetype.name().equalsIgnoreCase(value.trim())) {
                return archetype;
            }
        }
        return NONE;
    }
}
