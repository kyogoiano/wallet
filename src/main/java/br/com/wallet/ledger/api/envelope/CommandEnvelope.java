package br.com.wallet.ledger.api.envelope;

import java.time.Instant;
import java.util.UUID;

public record CommandEnvelope<T>(
        UUID operationId,
        String type,
        Instant timestamp,
        T payload
) {}
