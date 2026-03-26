package br.com.wallet.domain.context;

import br.com.wallet.application.aspects.tracing.TraceContext;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public record Wallet(@NonNull BigDecimal initialBalance,
                     @NonNull UUID operationId) implements TraceContext {
    @Override
    public UUID operationId() {
        return this.operationId;
    }
    @Override
    public Map<String, String> traceTags() {
        return Collections.emptyMap();
    }
}
