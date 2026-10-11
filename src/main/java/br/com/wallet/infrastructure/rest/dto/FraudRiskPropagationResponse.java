package br.com.wallet.infrastructure.rest.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record FraudRiskPropagationResponse(
    UUID rootSourceId,
    Instant evaluatedAt,
    int pathsEvaluated,
    String modelVersion,
    Map<UUID, TargetRiskDto> propagatedRisks
) {
    public record TargetRiskDto(
        UUID entityId,
        double propagatedRisk,
        int shortestHopCount,
        String primaryRelationship,
        Instant evaluatedAt
    ) {}
}
