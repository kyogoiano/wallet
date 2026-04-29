package br.com.wallet.core.tracing;

import java.util.Map;
import java.util.UUID;

public interface TraceContext {
    UUID operationId();
    Map<String, String> traceTags();
}
