package br.com.wallet.edge.internal.ingress;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Condition gating Edge HTTP ingress endpoints based on runtime mode (REQ-PRC-002, REQ-PRC-012).
 * Active when:
 * 1. wallet.runtime.mode=monolith, OR
 * 2. wallet.edge.enabled=true (default for Edge runtime).
 * Disabled when wallet.runtime.mode=multi-process and wallet.edge.enabled=false (Core headless).
 */
public class OnEdgeIngressCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, @NonNull AnnotatedTypeMetadata metadata) {
        Environment env = context.getEnvironment();
        String mode = env.getProperty("wallet.runtime.mode", "multi-process");
        if ("monolith".equalsIgnoreCase(mode)) {
            return true;
        }
        return env.getProperty("wallet.edge.enabled", Boolean.class, false);
    }
}
