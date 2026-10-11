package br.com.wallet.edge.internal.ingress;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation gating Edge HTTP ingress endpoints based on runtime mode (REQ-PRC-002, REQ-PRC-012).
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnEdgeIngressCondition.class)
public @interface ConditionalOnEdgeIngress {
}
