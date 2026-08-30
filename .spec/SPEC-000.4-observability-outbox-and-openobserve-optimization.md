# 📋 Specification: SPEC-000.4 — Outbox Observability, OpenObserve Pipeline & Source-Level Telemetry Optimization (History 11)

- **Status**: Completed & Verified
- **Author**: Antigravity Financial Architecture Team
- **Date**: 2026-08-30
- **Source Reference**: [`.histories/hitstory11-observability.txt`](file:///.histories/hitstory11-observability.txt)
- **Target Release / Milestone**: Wallet Service V4 — Phase 0.4 (Observability & Reliability Baseline)
- **Architectural Mantra**: *"Zero blind spots with zero bloat: filter and tag at the source, keep the collector ultralight, and correlate 100% of operations to deterministic operationId and userId."*

---

## 1. Intent & Business Value

Integrating distributed tracing, structured logging, and metric telemetry with OpenObserve requires high precision without introducing telemetry bloat, memory leaks, or heavy container overhead.

This specification addresses the observability requirements from `History 11` and the Outbox Relay using a **source-level optimization strategy**:
1. **Per-Event Relay Baggage**: Logs emitted during outbox event publishing (`log.info("Outbox event marked as processed! ...")`, `publisher.publish(...)`, and error logs) currently display an empty `operationId=` tag because background timer threads lack HTTP context.
2. **Span Lifecycle & Leaks in `TracingAspect`**: Methods annotated with `@Traceable` that do not receive a `TraceContext` or `UUID` argument (such as `OutboxRelay.process()`) start a scoped span in `TracingAspect` that is never closed when `operationId == null`.
3. **Optional `user.id` Baggage**: Support seamless user-level trace correlation across distributed calls by extracting `userId` from `TraceContext` and injecting it as baggage and span tag.
4. **Source-Level Cardinality & Noise Filtering**: Instead of relying on heavy Contrib collector processors (`transform`/`filter`), we filter metrics (`MeterFilter.ignoreTags("spring.bean.name")`) and synthetic health checks (`ObservationPredicate`) **directly inside `wallet-app`**, preventing wasteful serialization and CPU/network overhead.
5. **Ultralight Core Collector (`otel/opentelemetry-collector:latest`)**: The collector uses only standard core components (`batch` processor, OTLP HTTP/gRPC receivers, and OpenObserve HTTP/Gzip exporter), keeping its footprint at ~35 MB image size and ~20-30 MB RAM.

---

## 2. Scope & Non-Goals

### In Scope
- **`REQ-OBS-001` (Declarative Per-Event Outbox Baggage & MDC Correlation)**: `OutboxEvent` implements `TraceContext`, and `OutboxEventProcessor` processes each event under `@Traceable("outbox.relay.event")`.
- **`REQ-OBS-002` (TracingAspect Span Lifecycle Guarantee)**: Fix span leak in `TracingAspect` ensuring `span.end()` is unconditionally called in a `finally` block regardless of whether `operationId` is present or null.
- **`REQ-OBS-003` (Source-Level Metric Cardinality Control - History 11)**: Configure `MeterFilter.ignoreTags("spring.bean.name")` in `OpenTelemetryConfiguration.java` to strip verbose metric attributes before OTLP export.
- **`REQ-OBS-004` (Source-Level Health-Check & Noise Span Filtering - History 11)**: Configure `ObservationPredicate` in `OpenTelemetryConfiguration.java` to suppress `/actuator/health` synthetic spans at the source.
- **`REQ-OBS-005` (Optional `user.id` Baggage & Multi-Baggage Lifecycle)**: Extract `userId` from `TraceContext`, tag `user.id` on active spans, manage multiple baggage scopes (`operation.id` and `user.id`), and close them safely in `finally`.
- **`REQ-OBS-006` (Ultralight OTel Collector Tuning)**: Configure `docker/otel-collector-config.yml` using standard core `batch` processor (`send_batch_size: 1024`, `timeout: 200ms`, `send_batch_max_size: 2048`) compatible with `otel/opentelemetry-collector:latest`.
- **`REQ-OBS-007` (OpenObserve Container Optimization)**: Configure `openobserve` in `docker-compose.yaml` with `ZO_TELEMETRY=false` (disabling phone-home telemetry) and native Parquet storage persistence.

### Non-Goals
- Changing the transactional boundary of `OutboxRelay` or `useCase.handle` (covered in `SPEC-000.3`).
- Replacing OpenObserve or OpenTelemetry Collector with another telemetry backend.

---

## 3. Mathematical & Architectural Invariants

- **`I-OBS-001` (Universal Operation Baggage Correlation)**: Every log entry and distributed span generated during the processing of a domain event or transaction MUST contain a non-empty `operationId` matching the originating business operation.
- **`I-OBS-002` (Zero Span Leaks)**: Every span initialized by `@Traceable` or manual `tracer.startScopedSpan(...)` MUST be closed in a guaranteed `finally` block.
- **`I-OBS-003` (Zero Impact on Failure Isolation)**: Telemetry creation, baggage scoping, or collector failures must NEVER prevent outbox state persistence (`markAsProcessed`, `markFailed`, `markAsDead`) or domain transactions.
- **`I-OBS-004` (Bounded Metric Cardinality at Source)**: High-cardinality framework attributes (`spring.bean.name`) must be suppressed at the application source before OTLP transmission.

---

## 4. Requirements & Acceptance Criteria

### REQ-OBS-001: Declarative Per-Event Outbox Baggage & MDC Injection
- **Given** an outbox event with `aggregateId` (representing `operationId`).
- **When** `OutboxEventProcessor.processEvent` processes the event under `@Traceable("outbox.relay.event")`.
- **Then** a scoped trace/baggage block must be created with `operation.id = event.aggregateId().toString()`, `event.id = event.id().toString()`, and `event.type = event.eventType().name()`.
- **And** all logs generated during `publisher.publish(...)` and `outboxDao.markAsProcessed` must display `[operationId=<aggregateId>]`.

### REQ-OBS-002: TracingAspect Span Lifecycle
- **Given** a method annotated with `@Traceable("span.name")` without a `UUID` or `TraceContext` argument (such as `OutboxRelay.process()`).
- **When** the method is invoked and executes.
- **Then** the scoped span must start, record exceptions if thrown, and unconditionally close via `span.end()` in a `finally` block.

### REQ-OBS-003: Metric Cardinality Suppression at Source (History 11)
- **Given** JVM and application metrics emitted by Micrometer.
- **When** registered in the meter registry.
- **Then** `MeterFilter.ignoreTags("spring.bean.name")` must discard `spring.bean.name` tags before OTLP metric export.

### REQ-OBS-004: Health Check Noise Filtering at Source (History 11)
- **Given** synthetic probes and health checks on `/actuator/health`.
- **When** requests arrive at `wallet-app`.
- **Then** `ObservationPredicate` must suppress observation/span creation.

### REQ-OBS-005: Optional `user.id` Baggage Scope
- **Given** a `TraceContext` containing `userId`.
- **When** processed by `TracingAspect`.
- **Then** `user.id` is tagged on the span and added to W3C Baggage, with both `operation.id` and `user.id` scopes closed in `finally`.

### REQ-OBS-006: Ultralight OTel Collector Batch Tuning
- **Given** `otel/opentelemetry-collector:latest` (~35 MB image).
- **When** receiving OTLP data on `:4318` (HTTP) or `:4317` (gRPC).
- **Then** it batches events (`send_batch_size: 1024`, `timeout: 200ms`, `send_batch_max_size: 2048`) and exports to OpenObserve with Gzip compression without requiring heavy Contrib plugins.
