# 📝 Task Breakdown: TASKS-000.7 — Fraud Behavioral Embeddings, Archetype Matching & Evidence-Grounded Investigation Intelligence

- **Associated Spec**: [`SPEC-000.7-fraud-behavioral-embeddings-and-investigation-pgvector.md`](file:///.spec/SPEC-000.7-fraud-behavioral-embeddings-and-investigation-pgvector.md)
- **Associated Plan**: [`PLAN-000.7-fraud-behavioral-embeddings-and-investigation-pgvector.md`](file:///.spec/PLAN-000.7-fraud-behavioral-embeddings-and-investigation-pgvector.md)
- **Status**: Ready for Implementation (Histories 20, 24, 25, 26, 27 & 28)
- **Target Release**: Wallet Service V4.x — Phase 0.7

---

## 1. Traceability Matrix

| Requirement / Invariant | Planned Verification Test | Task IDs |
| :--- | :--- | :--- |
| `REQ-VEC-001` (pgvector Schema & Co-Location) | `PostgresEntityFeaturesDaoIT.shouldPersistAndRetrieveVector16()` | `TASK-2.1`, `TASK-2.2` |
| `REQ-VEC-002` (16-D Feature Taxonomy across 7 Domains) | `FeatureVectorExtractorTest.shouldExtractAll16BoundedDimensions()` | `TASK-1.1`, `TASK-1.2` |
| `REQ-VEC-003` (Exact Dot-Product Archetype Matching) | `ArchetypeCentroidMatcherTest.shouldComputeExactDotProductSimilarity()` | `TASK-3.1`, `TASK-3.2` |
| `REQ-VEC-004` (Hard Sanitization Boundary & PII Masking)| `PiiMaskingServiceTest.shouldCreateSanitizedInferenceContext()` | `TASK-4.1`, `TASK-4.2` |
| `REQ-VEC-005` (Pluggable Local Inference Client SPI) | `LocalInferenceClientTest.shouldInvokeOllamaWithStructuredGrammar()` | `TASK-5.1`, `TASK-5.2` |
| `REQ-VEC-006` (Dossier Assembly & Graceful Degradation) | `DefaultInvestigationServiceTest.shouldAssembleDossierWithDegradation()` | `TASK-5.1`, `TASK-5.2` |
| `REQ-VEC-007` (Hardware-Aware Benchmark Gate) | `ModelEvaluationHarnessTest.shouldValidateAgainstGoldStandardCases()` | `TASK-6.1`, `TASK-6.2`, `TASK-6.3` |
| `REQ-VEC-008` (Feature Magnitude Preservation) | `FeatureNormalizerTest.shouldPreserveMagnitudeAndUnitVector()` | `TASK-1.1`, `TASK-1.2` |
| `REQ-VEC-009` (Formal Claim Grounding Validator) | `ClaimGroundingValidatorTest.shouldEnforceClaimTypeAndEvidenceReferences()` | `TASK-5.1`, `TASK-5.2` |
| `REQ-VEC-010` (Deterministic Action Policy) | `RecommendedActionPolicyTest.shouldDeriveActionsDeterministically()` | `TASK-4.1`, `TASK-4.2` |
| `REQ-VEC-011` (Asynchronous Embedding Job Queue) | `PostgresEmbeddingJobDaoIT.shouldEnforceUniqueActiveJobsAndTokenLeases()` | `TASK-3.4`, `TASK-3.5` |
| `REQ-VEC-012` (Containerized SLM Integration Testing)| `OllamaInferenceClientIT.shouldGenerateSchemaCompliantNarrativeWithContainer()`| `TASK-5.4`, `TASK-5.5` |
| `I-VEC-001` (Normalized Vector Space & Magnitude) | `FeatureNormalizerTest.shouldGuaranteeUnitLengthWhenMagnitudePositive()` | `TASK-1.1`, `TASK-1.2` |
| `I-VEC-002` (Durable Co-Location) | `PostgresEntityFeaturesDaoIT.shouldPersistInPostgresDatabase()` | `TASK-2.1`, `TASK-2.2` |
| `I-VEC-003` (Deterministic Risk & Action Ownership) | `DefaultInvestigationServiceTest.shouldNeverPermitLLMToMutateRiskOrActions()`| `TASK-5.1`, `TASK-5.2` |
| `I-VEC-004` (Air-Gapped Local Inference) | `LocalInferenceClientTest.shouldNeverRouteToExternalNetwork()` | `TASK-5.1`, `TASK-5.2` |
| `I-VEC-005` (Strict Schema Adherence) | `ClaimGroundingValidatorTest.shouldRejectInvalidJsonOrMissingFields()` | `TASK-5.1`, `TASK-5.2` |
| `I-VEC-006` (Evidence Grounding & Zero Hallucination) | `ClaimGroundingValidatorTest.shouldRejectClaimsReferencingMissingEvidenceIds()`| `TASK-5.1`, `TASK-5.2` |
| `I-VEC-007` (Zero Hot-Path Impact) | `EmbeddingJobWorkerTest.shouldExecuteInWorkerPoolWithoutBlockingCore()` | `TASK-3.4`, `TASK-3.5` |
| `I-VEC-008` (Zero Activity Neutrality) | `FeatureNormalizerTest.shouldReturnZeroVectorAndNoneForZeroMagnitude()` | `TASK-1.1`, `TASK-1.2` |
| `I-VEC-009` (Investigation Resilience & Degradation)| `DefaultInvestigationServiceTest.shouldReturnDossierWhenInferenceFails()` | `TASK-5.1`, `TASK-5.2` |
| `I-VEC-010` (Real Inference Path Verification) | `OllamaInferenceClientIT.shouldVerifyRealInferencePipelineInsideDocker()` | `TASK-5.4`, `TASK-5.5` |

---

## 2. Implementation Tasks (TDD Order)

### Phase 1: Feature Extraction & Dual Normalization (`:fraud:embeddings`)
- [x] `TASK-1.1` [RED]: Write unit tests:
  - `FeatureVectorExtractorTest`: Testing extraction and clamping of all 16 dimensions across the 7 semantic groups.
  - `FeatureNormalizerTest`: Testing unit $L_2$ normalization ($\|\vec{v}\|_2 = 1.0$), magnitude preservation ($M = \|\vec{d}\|_2$), zero-activity neutrality (`I-VEC-008`), and `shouldDistinguishLowAndHighIntensityProfilesWithSameDirection()`.
- [x] `TASK-1.2` [GREEN]: Implement feature engineering components in `br.com.wallet.fraud.embeddings`:
  - `BehavioralFeatureVector` domain record (vector, magnitude, transaction count, transaction volume).
  - `FeatureVectorExtractor` (computes raw bounded metrics from transactional history).
  - `FeatureNormalizer` (computes unit vector and magnitude, enforcing `I-VEC-008`).
- [x] `TASK-1.3` [REFACTOR]: Optimize normalization math, ensure clamp safety and floating point precision stability.

### Phase 2: Database Schema & pgvector Persistence (`:fraud:embeddings`)
- [x] `TASK-2.1` [RED]: Write integration tests `PostgresEntityFeaturesDaoIT` and `PostgresArchetypeCentroidDaoIT` using Testcontainers PostgreSQL verifying:
  - Table persistence of `fraud_entity_features` with `vector(16)`, `feature_magnitude`, count, and volume.
  - Seeding and retrieval of `fraud_archetype_centroids`.
  - Exact dot-product SQL execution.
- [x] `TASK-2.2` [GREEN]: Update `docker/init/schema.sql`:
  - Enable `vector` extension.
  - Create `fraud_entity_features` table with magnitude and volume columns.
  - Create `fraud_archetype_centroids` table and seed data (`MONEY_MULE_RAPID_DRAIN`, `SMURFING`, `ACCOUNT_TAKEOVER`).
  - Create `fraud_embedding_jobs` table with token leases and active unique index.
  - Implement `PostgresEntityFeaturesDao` (implements `BehavioralFeatureStore`) and `PostgresArchetypeCentroidDao`.
- [x] `TASK-2.3` [REFACTOR]: Ensure connection pooling and JDBC prepared statement optimization.

### Phase 3: Exact Archetype Matching Engine (`:fraud:embeddings`)
- [x] `TASK-3.1` [RED]: Write unit tests `ArchetypeCentroidMatcherTest` and `BehavioralEmbeddingEngineTest` verifying:
  - Exact dot-product calculation between entity vector and archetype centroids ($O(N)$).
  - Exposure of both `directionalSimilarity` and `behavioralIntensity` in `ArchetypeMatch`.
  - Multiplier by archetype weight $w_i$.
  - Handling of zero-activity profiles returning risk $0.0$ and `"NONE"` (`I-VEC-008`).
  - Updating `fraud_entities.behavioral_risk` without mutating `direct_risk`, `graph_risk`, or `propagated_risk` (`I-PROP-005`).
- [x] `TASK-3.2` [GREEN]: Implement:
  - `DefaultArchetypeMatcher` (implements `ArchetypeMatchingService`).
  - `DefaultBehavioralEmbeddingEngine` (implements `BehavioralEmbeddingEngine`).
  - Configure Spring Modulith `@NamedInterface("embeddings-api")` and `@NamedInterface("embeddings-spi")`.
- [x] `TASK-3.3` [REFACTOR]: Clean encapsulation and interface contracts.

### Phase 3.5: Asynchronous Embedding Job Execution (`:fraud:embeddings`)
- [x] `TASK-3.4` [RED]: Write integration tests `PostgresEmbeddingJobDaoIT` and `EmbeddingJobWorkerTest` verifying:
  - Idempotent job enqueuing with status `PENDING`, explicit `as_of`, and active duplicate rejection (`I-VEC-011`).
  - Worker claim via `SELECT ... FOR UPDATE SKIP LOCKED` acquiring `worker_token` and `lease_until`.
  - Safe completion matching `worker_token`.
  - Reaper recovery of expired leases (`RUNNING` with `lease_until < now()`).
  - Asynchronous background execution without blocking core payment threads (`I-VEC-007`).
- [x] `TASK-3.5` [GREEN]: Implement:
  - `EmbeddingJobRepository` / `PostgresEmbeddingJobDao`.
  - `DefaultEmbeddingDispatcher` (implements `EmbeddingEvaluationDispatcher`).
  - `EmbeddingJobWorker` (background executor with token leases and heartbeats).
- [x] `TASK-3.6` [REFACTOR]: Tune batch claiming and worker concurrency.

### Phase 4: Evidence Context, Hard Sanitization Boundary & Deterministic Policy (`:fraud:investigation`)
- [x] `TASK-4.1` [RED]: Write unit tests `InvestigationContextBuilderTest`, `RiskClassificationPolicyTest`, and `RecommendedActionPolicyTest` verifying:
  - Assembly of `InvestigationEvidence` with structured atomic evidence items (`GRAPH-xxx`, `TEMPORAL-xxx`, `ARCHETYPE-xxx`).
  - Pre-fusion severity evaluation (`EVIDENCE_POLICY`) producing `RiskClassification`.
  - Derivation of allowable `RecommendedAction` sets based on deterministic thresholds (`REQ-VEC-010`).
  - Hard sanitization boundary: `PiiMaskingService` producing `SanitizedInferenceContext` with zero raw PII leakage (`REQ-VEC-004`).
- [x] `TASK-4.2` [GREEN]: Implement:
  - `InvestigationContextBuilder`.
  - `RiskClassificationPolicy` and `RecommendedActionPolicy`.
  - `PiiMaskingService` and `SanitizedInferenceContext`.
  - Domain records: `InvestigationEvidence`, `FraudRiskSnapshot`, `RecommendedAction`, `RiskClassification`.
- [x] `TASK-4.3` [REFACTOR]: Immutable records and strict sanitization guarantees.

### Phase 5: Local Inference SPI, Grounding Validator & Graceful Degradation (`:fraud:investigation`)
- [x] `TASK-5.1` [RED]: Write unit tests `ClaimGroundingValidatorTest`, `DefaultInvestigationServiceTest`, and `OllamaInferenceClientTest` verifying:
  - Validation of `InvestigationClaim`: mandatory `ClaimType`, non-empty summary, and all `evidenceReferences` matching existing evidence IDs.
  - Rejection of claims referencing missing IDs or contradictory facts (`I-VEC-006`).
  - RestClient communication to Ollama `/api/chat` with structured `"format": "json"`.
  - Graceful degradation (`I-VEC-009`): If inference client throws exception, times out, or returns invalid schema, returns dossier with deterministic evidence and `status = INFERENCE_UNAVAILABLE`.
- [x] `TASK-5.2` [GREEN]: Implement:
  - `ClaimGroundingValidator`.
  - `LocalInferenceClient` SPI in `br.com.wallet.fraud.investigation.spi` with `@NamedInterface("investigation-spi")`.
  - `StructuredInferenceRequest`, `InferenceCapability` (`FAST`, `BALANCED`, `HIGH_QUALITY`), and `InferenceModelProfile`.
  - `OllamaInferenceClient` concrete implementation using Spring `RestClient` with structured JSON parsing.
  - `DefaultInvestigationService` (implements `InvestigationService`).
  - Configure Spring Modulith `@NamedInterface("investigation-api")`.
- [x] `TASK-5.3` [REFACTOR]: Resilience timeouts, circuit breaker fallback, and structured prompt templates.

### Phase 5.5: Containerized Local SLM Integration Testing (Testcontainers & History 30)
- [x] `TASK-5.4` [RED]: Write `OllamaInferenceClientIT` (`REQ-VEC-012`, `I-VEC-010`) using Testcontainers Ollama:
  - Boots containerized Ollama runtime inside Docker network.
  - Verifies model availability (`smollm2:360m-instruct-q5_K_M`).
  - Executes real HTTP request with `StructuredInferenceRequest` via Spring `RestClient`.
  - Asserts response parses into `InvestigationNarrative` with non-empty executive summary.
  - Integrates with `ClaimGroundingValidator` to verify zero hallucinated IDs and consistent facts against raw evidence.
  - Verifies graceful degradation fallback (`I-VEC-009`) when container endpoint is forcefully unreachable or times out.
- [x] `TASK-5.5` [GREEN]: Implement Testcontainers Ollama fixture & configuration:
  - Add Ollama container configuration (`ollama/ollama:latest` with healthcheck on `/api/tags` and model pull/warmup).
  - Configure Spring test property `fraud.investigation.ollama.base-url` pointing to Testcontainers mapped port.
  - Configure execution tag `@Tag("integration-slm")` to allow running fast unit tests separate from heavy containerized SLM tests.
- [x] `TASK-5.6` [BENCHMARK]: Enhance `ModelEvaluationHarness` with `ModelCandidate` record:
  - Add `ModelCandidate(String id, String backend, InferenceCapability capability)`.
  - Support multi-candidate comparison (`smollm2:135m`, `smollm2:360m`, `smollm2:1.7b`).
  - Generate comparative `ModelEvaluationReport` measuring schema validity, grounding accuracy, and latency trends across gold standard cases (`CASE-001` through `CASE-004`).


### Phase 6: Model Evaluation Harness & Benchmark Gate (`:fraud:investigation`)
- [x] `TASK-6.1` [RED]: Create `ModelEvaluationHarnessTest` and gold-standard evaluation fixtures in `src/test/resources/fraud-investigation/`:
  - `CASE-001-money-mule.json`
  - `CASE-002-smurfing.json`
  - `CASE-003-account-takeover.json`
  - `CASE-004-low-risk-neutral.json`
- [x] `TASK-6.2` [GREEN]: Implement `ModelEvaluationHarness`:
  - Measurement of JSON Schema validity (asserting 100%).
  - Measurement of claim grounding validity and hallucinated evidence detection (asserting 100% reference fidelity).
  - Collection of TTFT (Time to First Token) and total latency.
  - Verification against configurable `InferenceBenchmarkThresholds`.
- [x] `TASK-6.3` [GREEN]: Implement benchmark gate assertions:
  - JSON validity = 100%.
  - Evidence reference validity = 100%.
  - Unsupported claims = 0.
  - Latency compliance with configured hardware profile budget.
- [x] `TASK-6.4` [REFACTOR]: Benchmark report generator and regression evaluation harness.

### Phase 7: REST API Exposure & Practical Verification Gate
- [x] `TASK-7.1` [RED]: Write controller unit test `FraudEmbeddingsControllerTest` and `FraudInvestigationControllerTest`.
- [x] `TASK-7.2` [GREEN]: Implement:
  - `POST /api/v1/fraud/intelligence/embeddings/extract/{entityId}`.
  - `GET /api/v1/fraud/intelligence/investigation/dossier/{entityId}`.
  - DTOs: `ExtractEmbeddingsResponse`, `InvestigationDossierResponse`.
- [x] `TASK-7.3` [GREEN]: Author `SUMMARY-000.7` with practical verification guide, seed SQL fixtures, cURL commands, and state validation queries per `I-SDD-002`.
