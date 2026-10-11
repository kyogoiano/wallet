package br.com.wallet.fraud.investigation.api.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Public response contract representing a strongly typed AI decision evaluation (REQ-TYPED-018).
 */
public record TypedEvaluationResponse(
    @NonNull UUID entityId,
    @NonNull String evaluationId,
    @NonNull String evidenceHash,
    @NonNull List<QuestionOutcomeDto> outcomes,
    @NonNull CompoundAssessmentDto compoundAssessment
) {
    public record QuestionOutcomeDto(
        @NonNull String questionId,
        @NonNull String questionName,
        @NonNull String valueType,
        @NonNull String outcomeType,
        @Nullable Object value,
        @Nullable String confidence,
        @Nullable String explanation,
        @Nullable String unavailableReason,
        @Nullable String modelIdentifier,
        @Nullable Long latencyMs
    ) {}

    public record CompoundAssessmentDto(
        @NonNull String status,
        @NonNull Set<String> triggeredSignals,
        @NonNull List<String> unavailables,
        @NonNull String explanation
    ) {}
}
