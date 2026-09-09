# 📐 Architecture Plan: PLAN-000.4 — Outbox Observability, OpenObserve Pipeline & Source-Level Telemetry Optimization (History 11)

- **Associated Spec**: [`SPEC-000.4-observability-outbox-and-openobserve-optimization.md`](file:///.spec/SPEC-000.4-observability-outbox-and-openobserve-optimization.md)
- **Status**: Completed & Verified
- **Author**: Antigravity Financial Architecture Team
- **Date**: 2026-08-30
- **Source Reference**: [`.histories/history11-observability.txt`](file:///.histories/history11-observability.txt)


---

## 1. Technical Architecture & Component Changes

```mermaid
flowchart TD
    subgraph App ["wallet-app (Source-Level Filtering)"]
        MF[MeterFilter: Ignore spring.bean.name]
        OP[ObservationPredicate: Suppress /actuator/health]
        OE[OutboxEvent implements TraceContext]
        OEP[OutboxEventProcessor @Traceable outbox.relay.event]
        TA[TracingAspect: Tag operation.id, user.id & close scopes]
    end

    subgraph Collector ["otel-collector (:4318 / :4317 - Core ~35MB Image)"]
        Batch[Batch: 1024 / 200ms]
    end

    subgraph Storage ["OpenObserve (:5080)"]
        OO[Parquet Columnar Storage: default/traces, default/logs, default/metrics]
    end

    App -->|Clean OTLP HTTP/Protobuf| Collector
    Collector --> Batch
    Batch -->|OTLP HTTP Gzip| Storage
```

---

## 2. Detailed Component Implementation

### 2.1. `OpenTelemetryConfiguration.java` (Source-Level Optimization)
```java
@Bean
MeterFilter ignoreSpringBeanName() {
    return MeterFilter.ignoreTags("spring.bean.name");
}

@Bean
ObservationPredicate noActuatorObservations() {
    return (name, context) -> {
        if (context instanceof ServerRequestObservationContext serverContext) {
            String uri = serverContext.getCarrier().getRequestURI();
            return uri == null || !uri.startsWith("/actuator/health");
        }
        return true;
    };
}
```

### 2.2. `TracingAspect.java` (Multi-Baggage Scope & Lifecycle)
```java
BaggageInScope opBaggage = null;
BaggageInScope userBaggage = null;

if (operationId != null) {
    span.tag(OPERATION_ID, operationId);
    opBaggage = tracer.createBaggageInScope(OPERATION_ID, operationId);
}

if (userId != null) {
    span.tag(USER_ID, userId);
    userBaggage = tracer.createBaggageInScope(USER_ID, userId);
}

try {
    return pjp.proceed();
} catch (IdempotencyException ex) {
    span.tag("status", "IDEMPOTENT_IGNORE");
    throw ex;
} catch (Throwable ex) {
    span.error(ex);
    span.tag("status", "FAILED");
    throw ex;
} finally {
    if (userBaggage != null) {
        try { userBaggage.close(); } catch (Exception ignored) {}
    }
    if (opBaggage != null) {
        try { opBaggage.close(); } catch (Exception ignored) {}
    }
    span.end();
}
```

### 2.3. `OutboxEvent.java` & `OutboxEventProcessor.java`
- `OutboxEvent` implements `TraceContext`: `operationId() = aggregateId`, `traceTags() = { event.id, event.type }`.
- `OutboxEventProcessor` annotated with `@Traceable("outbox.relay.event")`.
- `OutboxRelay` delegates batch processing to `eventProcessor.processEvent(event, now)`.

### 2.4. `docker/otel-collector-config.yml` (Ultra-Lightweight Core Config)
```yaml
receivers:
  otlp:
    protocols:
      http:
        endpoint: 0.0.0.0:4318
      grpc:
        endpoint: 0.0.0.0:4317

exporters:
  debug:
    verbosity: basic
  otlp_http/openobserve:
    endpoint: http://openobserve:5080/api/default
    headers:
      Authorization: Basic bGVhbmRyby51ZWdAZ21haWwuY29tOkFkbWluMTIzIUAj
    compression: gzip
    sending_queue:
      enabled: true
    retry_on_failure:
      enabled: true

processors:
  batch:
    send_batch_size: 1024
    timeout: 200ms
    send_batch_max_size: 2048

service:
  pipelines:
    metrics:
      receivers: [ otlp ]
      processors: [ batch ]
      exporters: [ debug, otlp_http/openobserve ]
    traces:
      receivers: [ otlp ]
      processors: [ batch ]
      exporters: [ debug, otlp_http/openobserve ]
    logs:
      receivers: [ otlp ]
      processors: [ batch ]
      exporters: [ debug, otlp_http/openobserve ]
```
