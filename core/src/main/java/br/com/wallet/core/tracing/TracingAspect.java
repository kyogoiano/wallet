package br.com.wallet.core.tracing;

import br.com.wallet.core.exceptions.IdempotencyException;
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
    public static final String OPERATION_ID = "operation.id";
    private final Tracer tracer;

    public TracingAspect(final Tracer tracer) {
        this.tracer = tracer;
    }

    @Around("@annotation(traceable)")
    public Object trace(final ProceedingJoinPoint pjp, final Traceable traceable) throws Throwable {
        log.debug("Aspect triggered for: {}", traceable.value());

        final ScopedSpan span = tracer.startScopedSpan(traceable.value());
        
        String operationId = null;
        for (final Object arg : pjp.getArgs()) {
            if (arg instanceof TraceContext ctx) {
                ctx.traceTags().forEach(span::tag);
                if (ctx.operationId() != null) {
                    operationId = ctx.operationId().toString();
                }
            }
            if (arg instanceof UUID uuid && operationId == null) {
                operationId = uuid.toString();
            }
        }

        // crates a baggage for cross-service propagation
        try (final BaggageInScope baggage = tracer.createBaggageInScope(OPERATION_ID, operationId)) {
            if (operationId != null) {
                span.tag(OPERATION_ID, operationId); // normalized names ( attribute promotion easily observable)
            }

            return pjp.proceed();

        } catch (IdempotencyException ex) {
            log.warn("IdempotencyException caught in aspect: {}", ex.getMessage());
            span.tag("status", "IDEMPOTENT_IGNORE");
            // Still re-throw so the Controller/Handler can catch it
            throw ex;
        } catch (RuntimeException ex) {
            log.error("RuntimeException caught in aspect: {}", ex.getMessage());
            span.error(ex);
            span.tag("status", "FAILED");
            throw ex;
        } catch (Throwable ex) {
            log.error("Throwable caught in aspect: {}", ex.getMessage());
            span.error(ex);
            span.tag("status", "FAILED");
            throw ex;
        } finally {
            span.end();
            log.debug("Aspect finished for: {}", traceable.value());
        }
    }
}
