package br.com.wallet.infrastructure.rest.dto;

import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.UUID;

public record DispatchEmbeddingResponse(
    @NonNull UUID entityId,
    @NonNull String status,
    @NonNull Instant asOf,
    @NonNull String message
) {}
