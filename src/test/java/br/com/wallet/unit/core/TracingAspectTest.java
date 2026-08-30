package br.com.wallet.unit.core;

import br.com.wallet.core.context.OperationOrigin;
import br.com.wallet.core.exceptions.IdempotencyException;
import br.com.wallet.core.tracing.TraceContext;
import br.com.wallet.core.tracing.Traceable;
import br.com.wallet.core.tracing.TracingAspect;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.ScopedSpan;
import io.micrometer.tracing.Tracer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TracingAspect Unit Tests (REQ-OBS-002, I-OBS-002)")
class TracingAspectTest {

    @Mock
    Tracer tracer;

    @Mock
    ScopedSpan scopedSpan;

    @Mock
    BaggageInScope baggageInScope;

    @Mock
    ProceedingJoinPoint joinPoint;

    @Mock
    Traceable traceable;

    @InjectMocks
    TracingAspect aspect;

    @BeforeEach
    void setUp() {
        when(traceable.value()).thenReturn("test.span");
        when(tracer.startScopedSpan(anyString())).thenReturn(scopedSpan);
    }

    @Test
    @DisplayName("Should unconditionally start and end span even when operationId is null (I-OBS-002)")
    void shouldCloseSpanEvenWhenOperationIdIsNull() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{"non-context-argument"});
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.trace(joinPoint, traceable);

        assertThat(result).isEqualTo("result");
        verify(tracer).startScopedSpan("test.span");
        verify(scopedSpan).end();
        verifyNoInteractions(baggageInScope);
    }

    @Test
    @DisplayName("Should create baggage scope and tag span when operationId UUID is present")
    void shouldCreateBaggageAndTagSpanWhenUUIDPresent() throws Throwable {
        UUID opId = UUID.randomUUID();
        when(joinPoint.getArgs()).thenReturn(new Object[]{opId});
        when(joinPoint.proceed()).thenReturn("success");
        when(tracer.createBaggageInScope(TracingAspect.OPERATION_ID, opId.toString())).thenReturn(baggageInScope);

        Object result = aspect.trace(joinPoint, traceable);

        assertThat(result).isEqualTo("success");
        verify(scopedSpan).tag(TracingAspect.OPERATION_ID, opId.toString());
        verify(tracer).createBaggageInScope(TracingAspect.OPERATION_ID, opId.toString());
        verify(baggageInScope).close();
        verify(scopedSpan).end();
    }

    @Test
    @DisplayName("Should extract operationId and tags from TraceContext")
    void shouldExtractFromTraceContext() throws Throwable {
        UUID opId = UUID.randomUUID();
        TraceContext ctx = new TraceContext() {
            @Override
            public UUID operationId() {
                return opId;
            }

            @Override
            public UUID userId() {
                return null;
            }

            @Override
            public Map<String, String> traceTags() {
                return Map.of("tag1", "val1");
            }
        };

        when(joinPoint.getArgs()).thenReturn(new Object[]{ctx});
        when(joinPoint.proceed()).thenReturn("ok");
        when(tracer.createBaggageInScope(TracingAspect.OPERATION_ID, opId.toString())).thenReturn(baggageInScope);

        Object result = aspect.trace(joinPoint, traceable);

        assertThat(result).isEqualTo("ok");
        verify(scopedSpan).tag("tag1", "val1");
        verify(scopedSpan).tag(TracingAspect.OPERATION_ID, opId.toString());
        verify(baggageInScope).close();
        verify(scopedSpan).end();
    }

    @Test
    @DisplayName("Should record error and tag FAILED when joinPoint throws RuntimeException")
    void shouldRecordErrorAndCloseSpanOnException() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{});
        RuntimeException expectedEx = new RuntimeException("DB Connection timeout");
        when(joinPoint.proceed()).thenThrow(expectedEx);

        assertThatThrownBy(() -> aspect.trace(joinPoint, traceable))
                .isSameAs(expectedEx);

        verify(scopedSpan).error(expectedEx);
        verify(scopedSpan).tag("status", "FAILED");
        verify(scopedSpan).end();
    }

    @Test
    @DisplayName("Should tag IDEMPOTENT_IGNORE and close span on IdempotencyException")
    void shouldTagIdempotentAndCloseSpanOnIdempotencyException() throws Throwable {
        UUID opId = UUID.randomUUID();
        when(joinPoint.getArgs()).thenReturn(new Object[]{opId});
        when(tracer.createBaggageInScope(TracingAspect.OPERATION_ID, opId.toString())).thenReturn(baggageInScope);

        IdempotencyException idempEx = new IdempotencyException("Already processed: " + opId);
        when(joinPoint.proceed()).thenThrow(idempEx);

        assertThatThrownBy(() -> aspect.trace(joinPoint, traceable))
                .isSameAs(idempEx);

        verify(scopedSpan).tag("status", "IDEMPOTENT_IGNORE");
        verify(baggageInScope).close();
        verify(scopedSpan).end();
    }

    @Test
    @DisplayName("Should create both operation.id and user.id baggage when present in TraceContext")
    void shouldCreateBothOperationAndUserBaggage() throws Throwable {
        UUID opId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BaggageInScope userBaggage = mock(BaggageInScope.class);

        TraceContext ctx = new TraceContext() {
            @Override
            public UUID operationId() {
                return opId;
            }

            @Override
            public UUID userId() {
                return userId;
            }

            @Override
            public Map<String, String> traceTags() {
                return Map.of("tenant", "prime");
            }
        };

        when(joinPoint.getArgs()).thenReturn(new Object[]{ctx});
        when(joinPoint.proceed()).thenReturn("done");
        when(tracer.createBaggageInScope(TracingAspect.OPERATION_ID, opId.toString())).thenReturn(baggageInScope);
        when(tracer.createBaggageInScope(TracingAspect.USER_ID, userId.toString())).thenReturn(userBaggage);

        Object result = aspect.trace(joinPoint, traceable);

        assertThat(result).isEqualTo("done");
        verify(scopedSpan).tag("tenant", "prime");
        verify(scopedSpan).tag(TracingAspect.OPERATION_ID, opId.toString());
        verify(scopedSpan).tag(TracingAspect.USER_ID, userId.toString());
        verify(tracer).createBaggageInScope(TracingAspect.OPERATION_ID, opId.toString());
        verify(tracer).createBaggageInScope(TracingAspect.USER_ID, userId.toString());
        verify(baggageInScope).close();
        verify(userBaggage).close();
        verify(scopedSpan).end();
    }
}
