package br.com.wallet.application.aspects.tracing;

import java.util.Map;

public interface TraceContext {
    Map<String, String> traceTags();
}
