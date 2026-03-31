package br.com.wallet.application.aspects.tracing;

import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.ScopedSpan;
import io.micrometer.tracing.Tracer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
public class TracingAspect {

    private static final Logger log = LoggerFactory.getLogger(TracingAspect.class);
    private final Tracer tracer;

    public TracingAspect(final Tracer tracer) {
        this.tracer = tracer;
    }

    @Around("@annotation(traceable)")
    public Object trace(final ProceedingJoinPoint pjp, final Traceable traceable) throws Throwable {

        final ScopedSpan span = tracer.startScopedSpan(traceable.value());
        log.info("Span started, context: {}", span.context());

        String operationId = null;

        // Extract operationId from arguments
        for (final Object arg : pjp.getArgs()) {
            if (arg instanceof TraceContext ctx) {
                ctx.traceTags().forEach(span::tag);
                if (ctx.operationId() != null) {
                    operationId = ctx.operationId().toString();
                }
            }

            // fallback (caso venha UUID direto)
            if (arg instanceof UUID uuid && operationId == null) {
                operationId = uuid.toString();
            }
        }


        // The baggage will automatically propagate this to MDC if configured in application.yaml
        try (final BaggageInScope baggage = tracer.createBaggageInScope("operationId", operationId)) {
                // standard tags!
                span.tag("class", pjp.getTarget().getClass().getSimpleName());
                span.tag("method", pjp.getSignature().getName());
                if(operationId != null) {
                    span.tag("operationId", operationId);
                }

                final Object result = pjp.proceed();

                span.tag("status", "SUCCESS");

                return result;

        } catch (Throwable ex) {
            span.error(ex);
            span.tag("status", "FAILED");
            throw ex;

        } finally {
            span.end();
            log.info("Span ended, context: {}", span.context());
        }
    }
}
