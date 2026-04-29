package br.com.wallet.domain.context;

import br.com.wallet.core.tracing.TraceContext;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record Wallet(@NonNull UUID id,
                     BigDecimal initialBalance,
                     UUID operationId) implements TraceContext {
    @Override
    public UUID operationId() {
        return this.operationId;
    }
    @Override
    public Map<String, String> traceTags() {
        return Map.of(
                "wallet.id", id.toString()
        );
    }
}
