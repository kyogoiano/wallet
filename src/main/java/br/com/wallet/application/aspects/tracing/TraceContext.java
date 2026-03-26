package br.com.wallet.application.aspects.tracing;

import java.util.Map;
import java.util.UUID;

public interface TraceContext {
    UUID operationId();
    Map<String, String> traceTags();
}
