package br.com.wallet.unit.edge;

import br.com.wallet.edge.api.CommandEnvelope;
import br.com.wallet.edge.api.CommandType;
import br.com.wallet.edge.internal.idempotency.EdgeIdempotencyGate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EdgeIdempotencyGate Unit Tests (TASK-3.7 & TASK-3.8)")
class EdgeIdempotencyGateTest {

    private EdgeIdempotencyGate idempotencyGate;

    @BeforeEach
    void setUp() {
        idempotencyGate = new EdgeIdempotencyGate();
    }

    @Test
    @DisplayName("Should accept new operationId")
    void shouldAcceptNewOperation() {
        UUID opId = UUID.randomUUID();
        CommandEnvelope command = CommandEnvelope.create(opId, CommandType.TRANSFER, "{\"amount\":100}", "127.0.0.1");

        EdgeIdempotencyGate.ValidationResult result = idempotencyGate.validateAndRecord(command);
        assertThat(result).isEqualTo(EdgeIdempotencyGate.ValidationResult.NEW);
    }

    @Test
    @DisplayName("Should detect idempotent replay for same operationId and identical payload")
    void shouldDetectIdempotentReplay() {
        UUID opId = UUID.randomUUID();
        String payload = "{\"from\":\"A\",\"to\":\"B\",\"amount\":50.00}";
        CommandEnvelope first = CommandEnvelope.create(opId, CommandType.TRANSFER, payload, "127.0.0.1");
        CommandEnvelope replay = CommandEnvelope.create(opId, CommandType.TRANSFER, payload, "127.0.0.1");

        assertThat(idempotencyGate.validateAndRecord(first)).isEqualTo(EdgeIdempotencyGate.ValidationResult.NEW);
        assertThat(idempotencyGate.validateAndRecord(replay)).isEqualTo(EdgeIdempotencyGate.ValidationResult.IDEMPOTENT_REPLAY);
    }

    @Test
    @DisplayName("Should canonicalize JSON payload and detect idempotent replay even with reordered keys")
    void shouldCanonicalizeJsonPayload() {
        UUID opId = UUID.randomUUID();
        String payload1 = "{\"from\":\"A\",\"to\":\"B\",\"amount\":50.00}";
        String payload2 = "{\"amount\":50.00,\"from\":\"A\",\"to\":\"B\"}";

        CommandEnvelope first = CommandEnvelope.create(opId, CommandType.TRANSFER, payload1, "127.0.0.1");
        CommandEnvelope replayWithReorderedKeys = CommandEnvelope.create(opId, CommandType.TRANSFER, payload2, "127.0.0.1");

        assertThat(idempotencyGate.validateAndRecord(first)).isEqualTo(EdgeIdempotencyGate.ValidationResult.NEW);
        assertThat(idempotencyGate.validateAndRecord(replayWithReorderedKeys)).isEqualTo(EdgeIdempotencyGate.ValidationResult.IDEMPOTENT_REPLAY);
    }

    @Test
    @DisplayName("Should reject conflicting payload for same operationId with CONFLICT (HTTP 409)")
    void shouldRejectConflictingPayloadForSameOperationId() {
        UUID opId = UUID.randomUUID();
        String payload1 = "{\"from\":\"A\",\"to\":\"B\",\"amount\":50.00}";
        String payload2 = "{\"from\":\"A\",\"to\":\"B\",\"amount\":999.00}";

        CommandEnvelope first = CommandEnvelope.create(opId, CommandType.TRANSFER, payload1, "127.0.0.1");
        CommandEnvelope conflict = CommandEnvelope.create(opId, CommandType.TRANSFER, payload2, "127.0.0.1");

        assertThat(idempotencyGate.validateAndRecord(first)).isEqualTo(EdgeIdempotencyGate.ValidationResult.NEW);
        assertThat(idempotencyGate.validateAndRecord(conflict)).isEqualTo(EdgeIdempotencyGate.ValidationResult.CONFLICT);
    }
}
