# 📝 Task Breakdown: TASKS-000.4 — Outbox Observability, OpenObserve Pipeline & Source-Level Telemetry Optimization (History 11)

- **Associated Spec**: [`SPEC-000.4-observability-outbox-and-openobserve-optimization.md`](file:///.spec/SPEC-000.4-observability-outbox-and-openobserve-optimization.md)
- **Associated Plan**: [`PLAN-000.4-observability-outbox-and-openobserve-optimization.md`](file:///.spec/PLAN-000.4-observability-outbox-and-openobserve-optimization.md)
- **Source Reference**: [`.histories/hitstory11-observability.txt`](file:///.histories/hitstory11-observability.txt)
- **Status**: Completed

---

## 1. Traceability Matrix

| Requirement / Invariant | Planned Verification Test | Task IDs |
| :--- | :--- | :--- |
| `REQ-OBS-002`, `I-OBS-002` | `TracingAspectTest.shouldCloseSpanEvenWhenOperationIdIsNull()` | `TASK-1.1`, `TASK-1.2` |
| `REQ-OBS-005` | `TracingAspectTest.shouldCreateBothOperationAndUserBaggage()` | `TASK-1.3` |
| `REQ-OBS-001`, `I-OBS-001` | `OutboxEventProcessorTest`, `OutboxRelayTest` | `TASK-2.1`, `TASK-2.2` |
| `REQ-OBS-003`, `REQ-OBS-004`, `I-OBS-004` | `OpenTelemetryConfiguration` MeterFilter and ObservationPredicate | `TASK-3.1` |
| `REQ-OBS-006`, `REQ-OBS-007` | `docker/otel-collector-config.yml` (lightweight core) and `docker-compose.yaml` | `TASK-3.2`, `TASK-3.3` |
| `I-OBS-003` | `OutboxIT` regression suite | `TASK-4.1` |

---

## 2. Implementation Tasks (TDD Order)

### Phase 1: TracingAspect Span Lifecycle, Multi-Baggage & Core Resilience
- [x] `TASK-1.1` [RED]: Write unit test in `TracingAspectTest` to verify that `span.end()` is invoked even when no `operationId` or `TraceContext` is provided in arguments.
- [x] `TASK-1.2` [GREEN]: Refactor `TracingAspect.java` ensuring `span.end()` is always enclosed in a `finally` block for all `@Traceable` joinpoints.
- [x] `TASK-1.3` [GREEN]: Add `user.id` optional baggage handling with safe multiple scope closure in `TracingAspect.java` and test in `TracingAspectTest.java`.

### Phase 2: OutboxRelay & OutboxEventProcessor Declarative Tracing
- [x] `TASK-2.1` [GREEN]: Make `OutboxEvent` implement `TraceContext` and extract `OutboxEventProcessor` with `@Traceable("outbox.relay.event")`.
- [x] `TASK-2.2` [GREEN]: Update `OutboxRelay` to delegate to `OutboxEventProcessor` and update unit tests (`OutboxRelayTest`, `OutboxEventProcessorTest`).

### Phase 3: Source-Level Telemetry Optimization & Lightweight Collector (History 11)
- [x] `TASK-3.1` [GREEN]: Add `MeterFilter.ignoreTags("spring.bean.name")` and `ObservationPredicate` for `/actuator/health` in `OpenTelemetryConfiguration.java`.
- [x] `TASK-3.2` [GREEN]: Configure `docker/otel-collector-config.yml` with ultra-light core `batch` processor and dual HTTP/gRPC receivers.
- [x] `TASK-3.3` [GREEN]: Keep `docker-compose.yaml` with `otel/opentelemetry-collector:latest` (~35 MB) and `ZO_TELEMETRY=false`.

### Phase 4: Verification & Summary
- [x] `TASK-4.1`: Verify `TracingAspectTest`, `OutboxRelayTest`, `OutboxEventProcessorTest`.
- [x] `TASK-4.2`: Generate `.spec/summaries/SUMMARY-000.4-observability-outbox-and-openobserve-optimization.md`.
