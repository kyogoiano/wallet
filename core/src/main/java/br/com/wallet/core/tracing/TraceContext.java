package br.com.wallet.core.tracing;

import java.util.Map;
import java.util.UUID;

public interface TraceContext {
    UUID operationId();
    UUID userId();
    Map<String, String> traceTags();
}
