package br.com.wallet.application.aspects.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TracingAspect {
    private final Tracer tracer;

    public TracingAspect(final Tracer tracer) {
        this.tracer = tracer;
    }

    @Around("@annotation(traceable)")
    public Object trace(final ProceedingJoinPoint pjp, final Traceable traceable) throws Throwable {

        final Span span = tracer.nextSpan()
                .name(traceable.value())
                .start();

        try (final Tracer.SpanInScope ws = tracer.withSpan(span)) {
            // standard tags!
            span.tag("class", pjp.getTarget().getClass().getSimpleName());
            span.tag("method", pjp.getSignature().getName());
            // dynamic tags
            for (final Object arg : pjp.getArgs()) {
                if (arg instanceof TraceContext ctx) {
                    ctx.traceTags().forEach(span::tag);
                }
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
        }
    }
}
