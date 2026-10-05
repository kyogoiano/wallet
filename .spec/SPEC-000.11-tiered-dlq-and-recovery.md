# 📋 Specification: SPEC-000.11 — Edge-to-Core Command Reliability, Retry & Reprocessing

- **Status**: 📝 **Draft (Rev. 3 — Aligned with History 79: Pure Edge-to-Core Ingress Focus)**
- **Author**: Antigravity Platform Resilience & Financial Systems Guild
- **Date**: 2026-10-02
- **Target Release / Milestone**: Wallet Service V4 — Phase 000.11
- **Bounded Context / Module**: Spring Modulith `br.com.wallet.dlq` (Edge-to-Core Command Reprocessing)
- **Spec Slicing Scope**: Max 250 lines (`I-SDD-006`). Live ingress isolation, bounded transient retries, quarantine, and operator governance.

---

## 0. Pre-Flight History & Context Audit

- **Histories & Summaries Audited**:
  - [`.histories/history79.txt`](file:///.histories/history79.txt): Restructured scope: 000.11 is exclusively the reliability mechanism for the Edge $\rightarrow$ Core process boundary. Removed internal domain event DLQ (intra-core events are managed in-process via Spring Modulith in 000.12).
  - [`.histories/history76.txt`](file:///.histories/history76.txt) & [`.histories/history77.txt`](file:///.histories/history77.txt): Uber Reliable Reprocessing model: unblock live traffic by routing failing commands into a separate reprocessing flow; distinct `EXHAUSTED` vs `QUARANTINED` states.
  - [`.histories/history75.txt`](file:///.histories/history75.txt): Mandated 7 MUST invariants: Edge admission non-persistence (`I-TDLQ-008`), `retry_count=0` for quarantine, crypto integrity replay prohibition.
  - [`SPEC-000.9`](file:///.spec/SPEC-000.9-reactive-edge-gateway-and-ingress-resilience.md) & [`SPEC-000.10`](file:///.spec/SPEC-000.10-financial-security.md): Edge admission and envelope encryption.
- **Foundational Constraints (`constitution.md`)**:
  - `I-LEDGER-001` (Immutable ledger), `I-ACCOUNT-001` (Active account gate), `I-ATOMICITY-001` (Single tx boundary), `I-SEC-012` (Non-emission first).

---

## 1. Intent & Reliable Reprocessing Principle

> **Reliable Reprocessing Principle**: A failed ingress command MUST leave the live processing path before automatic reprocessing begins. Retry processing MUST be isolated from live traffic and MUST be bounded. Retries preserve financial operation identity.

`SPEC-000.11` governs a single architectural responsibility:
> **"What happens when a financial command admitted by Edge Gateway fails during processing in Wallet Core?"**

It manages command reprocessing for external operations (`TransferFunds`, `Deposit`, `Withdraw`):
- Unblocks live NATS ingress traffic immediately by committing failed commands to durable recovery storage before ACK.
- Implements bounded automated retries ($\le 3$) with full decorrelated jitter for transient infrastructure failures.
- Immediately isolates non-retryable failures (permanent rejections, poison pills, security violations) into cold `QUARANTINED` storage with `retry_count = 0`.
- Preserves the financial `operationId` across retries and operator replays (`I-TDLQ-005`).

*(Note: Internal domain events between capabilities inside Wallet Core communicate in-process via Spring Modulith events per SPEC-000.12 and are excluded from this specification).*

---

## 2. Reprocessing Pipeline & State Machine

```text
                  Incoming Ingress Command (from Edge)
                                   │
                      ┌────────────┴────────────┐
              Admission Failure          Processing Failure
              (HMAC, Nonce, Time)                 │
                      │                  ┌────────┼────────┐
                Reject 401/400       TRANSIENT PERMANENT POISON/SECURITY
                NO DLQ WRITES            │        │        │
                                       FAILED     ▼        ▼
                                         │   QUARANTINED QUARANTINED
                                    retry < 3? (retry=0)   (retry=0)
                                     /      \     │           │
                                   yes       no   └─────┬─────┘
                                    │         │         │
                                    ▼         ▼         ▼
                                 PENDING  EXHAUSTED  Operator API
                                    │                 │  (RBAC: dlq:replay)
                                    ▼                 ├──> PENDING (preserve operationId)
                                PROCESSING            └──> DISCARDED
```

- **Failure Classification**:
  - `TRANSIENT`: DB lock timeouts, pool starvation, connection timeout, network blip. $\to$ Eligible for automated retry ($\le 3$).
  - `PERMANENT`: Non-retryable domain invariant violations (`AccountBlockedException`, insufficient funds). $\to$ `QUARANTINED`.
  - `POISON`: Malformed JSON, corrupted envelope framing, deserialization failures. $\to$ `QUARANTINED`.
  - `SECURITY`: Key rotation delay, policy failure. $\to$ `QUARANTINED`. (Cryptographic integrity failures like AEAD tag mismatch are strictly non-replayable).

---

## 3. Mathematical & System Invariants

- **`I-TDLQ-001` (Live Ingress Isolation)**: A failure during Core processing of an ingress command MUST NOT block subsequent live Edge commands while the failed command is being reprocessed.
- **`I-TDLQ-002` (Live Unblocking)**: When a command encounters a processing failure, the consumer MUST route it to the reprocessing state and ACK the original live stream message. Live traffic MUST NOT wait on retry delays.
- **`I-TDLQ-003` (Bounded Transient Retry with Jitter)**: Automated retries for `TRANSIENT` failures MUST NOT exceed 3 attempts and MUST apply decorrelated full jitter:
  $$\Delta t_r = \text{Uniform}(0, \min(60\text{s}, 2\text{s} \times 2^r)), \quad r \in \{1, 2, 3\}$$
  When $r \ge 3$, the operation transitions irreversibly to `EXHAUSTED`.
- **`I-TDLQ-004` (Terminal State Disjunction)**:
  $$\text{TerminalStates} = \text{EXHAUSTED} \cup \text{QUARANTINED}, \quad \text{EXHAUSTED} \cap \text{QUARANTINED} = \emptyset$$
  `EXHAUSTED` operations consumed 3 transient retries. `QUARANTINED` operations were isolated immediately with `retry_count = 0`.
- **`I-TDLQ-005` (Financial Identity Preservation)**:
  $$\text{Replay}(\text{cmd}) \implies \text{operationId}_{\text{replayed}} \equiv \text{operationId}_{\text{original}}, \quad \text{replayId} = \text{UUID}_{\text{new}}$$
  Manual or automated replay MUST preserve the original `operationId`. Replay attempts MUST NOT create new financial operations.
- **`I-TDLQ-006` (Cryptographic Opacity)**: DLQ storage for Core commands MUST store opaque `CryptoEnvelope` bytes. Plaintext financial data SHALL NEVER be persisted in DLQ tables (`I-ENV-001`).
- **`I-TDLQ-007` (Cryptographic Integrity Replay Prohibition)**: Operations failing AEAD tag verification or envelope framing proving ciphertext corruption MUST NOT be manually replayable under any circumstance.
- **`I-TDLQ-008` (Admission Security Non-Persistence)**: Unauthenticated Edge admission failures (invalid HMAC, nonce reuse, timestamp skew) MUST be rejected with HTTP 401/400 and MUST NOT create DLQ records.
- **`I-TDLQ-009` (Durable Handoff Before ACK)**:
  $$\text{ACK}(m) \implies \text{DurableHandoff}(m) = \text{committed}$$
  An ingress command consumer MUST NOT acknowledge the NATS message until execution succeeds OR the message is idempotently committed to the DLQ/reprocessing store.

---

## 4. Functional Requirements (MoSCoW Prioritized — `I-SDD-004`)

### 4.1 Pillar A: Core Command DLQ & Ingress Unblocking [MUST]
- **`REQ-TDLQ-001 [MUST]`**: Implement typed `FailureClassifier` in Core mapping exceptions to `TRANSIENT`, `PERMANENT`, `POISON`, `SECURITY`.
- **`REQ-TDLQ-002 [MUST]`**: Ingress command consumers route transient failures to the reprocessing table with `status = FAILED`, committing handoff before ACKing NATS (`I-TDLQ-002`, `I-TDLQ-009`).
- **`REQ-TDLQ-003 [MUST]`**: Edge Gateway rejects unauthenticated admission requests (`INVALID_HMAC`, `REPLAY_NONCE`) without invoking DLQ persistence (`I-TDLQ-008`).

### 4.2 Pillar B: Bounded Transient Replay Engine [MUST]
- **`REQ-TDLQ-004 [MUST]`**: Only `TRANSIENT` failures are eligible for automated retry. Non-transient failures transition immediately to `QUARANTINED` with `retry_count = 0` (`I-TDLQ-004`).
- **`REQ-TDLQ-005 [MUST]`**: Automated reprocessing applies full-jitter backoff (`I-TDLQ-003`) and transitions to `EXHAUSTED` once 3 retries are exceeded.
- **`REQ-TDLQ-006 [MUST]`**: Replayed command executions preserve the original `operationId` and attach a new `replayId` (`I-TDLQ-005`).

### 4.3 Pillar C: Forensic Quarantine & Operator Governance [MUST]
- **`REQ-TDLQ-007 [MUST]`**: Store raw `CryptoEnvelope` bytes for quarantined records (`I-TDLQ-006`) with sanitized error summaries (`I-SEC-012`). Raw stacktraces and plaintext commands are forbidden.
- **`REQ-TDLQ-008 [MUST]`**: Expose operator query API filtering by `failureType`, `tenantId`, and status (`EXHAUSTED`, `QUARANTINED`).
- **`REQ-TDLQ-009 [MUST]`**: Manual replay endpoint (`POST /dlq/operations/{id}/replay`) validates operator authorization (`dlq:replay`), preserves `operationId` (`I-TDLQ-005`), and rejects cryptographic integrity failures (`I-TDLQ-007`).
- **`REQ-TDLQ-010 [MUST]`**: Implement `POST /dlq/operations/{id}/discard` to permanently transition records to `DISCARDED` with immutable audit reasons.

### 4.4 Scope Fencing [WON'T]
- **`REQ-TDLQ-W01 [WON'T]`**: Reprocessing internal domain events (handled in-process via Spring Modulith per SPEC-000.12).
- **`REQ-TDLQ-W02 [WON'T]`**: Algorithmic guessing or automated synthetic repair of poison payloads.
- **`REQ-TDLQ-W03 [WON'T]`**: Edge spool journal mechanics (owned by `SPEC-000.9`).
