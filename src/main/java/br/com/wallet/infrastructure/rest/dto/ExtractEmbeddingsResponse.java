package br.com.wallet.infrastructure.rest.dto;

import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ExtractEmbeddingsResponse(
    @NonNull UUID entityId,
    double behavioralRisk,
    @NonNull String topArchetype,
    double archetypeSimilarity,
    double featureMagnitude,
    long transactionCount,
    @NonNull BigDecimal transactionVolume
) {}
