package br.com.wallet.infrastructure.rest.dto;

import java.time.Instant;
import java.util.UUID;

public record DispatchPropagationResponse(
    UUID jobId,
    UUID entityId,
    String status,
    Instant asOf,
    String modelVersion,
    String message
) {}
