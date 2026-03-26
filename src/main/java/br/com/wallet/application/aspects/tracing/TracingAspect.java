package br.com.wallet.application.aspects.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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

        final Span span = tracer.nextSpan()
                .name(traceable.value())
                .start();
        log.info("Span started, context: {}", span.context());

        String operationId = null;

        try (final Tracer.SpanInScope ws = tracer.withSpan(span)) {
            // standard tags!
            span.tag("class", pjp.getTarget().getClass().getSimpleName());
            span.tag("method", pjp.getSignature().getName());
            // dynamic tags
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

            includeMDC(operationId, span);

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

            MDC.remove("operationId");
            MDC.remove("traceId");
            MDC.remove("spanId");
        }
    }

    private static void includeMDC(String operationId, @NonNull Span span) {
        // 🔥 MDC enrichment
        if (operationId != null) {
            MDC.put("operationId", operationId);
            span.tag("operationId", operationId);
        }

        // também pegar traceId automático
        MDC.put("traceId", span.context().traceId());
        MDC.put("spanId", span.context().spanId());
    }
}
