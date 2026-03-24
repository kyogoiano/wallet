package br.com.wallet.application.aspects.tracing;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record TransferCommand(
        UUID from,
        UUID to,
        BigDecimal amount,
        UUID operationId
) implements TraceContext {

    @Override
    public Map<String, String> traceTags() {
        return Map.of(
                "operation.id", operationId.toString(),
                "wallet.from", from.toString(),
                "wallet.to", to.toString()
        );
    }
}
