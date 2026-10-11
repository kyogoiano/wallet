package br.com.wallet.edge.internal.journal.segmented;

import br.com.wallet.edge.api.CommandType;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable in-memory representation of a segmented journal record.
 * Binary format: 54 bytes header + N bytes payload.
 */
public record JournalRecord(
        long sequenceNumber,
        UUID operationId,
        long timestamp,
        CommandType commandType,
        byte[] payload,
        short version,
        short flags
) {
    public JournalRecord {
        Objects.requireNonNull(operationId, "operationId must not be null");
        Objects.requireNonNull(commandType, "commandType must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        payload = payload.clone();
    }

    public JournalRecord(long sequenceNumber, UUID operationId, long timestamp, CommandType commandType, byte[] payload) {
        this(sequenceNumber, operationId, timestamp, commandType, payload, (short) 1, (short) 0);
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JournalRecord that)) return false;
        return sequenceNumber == that.sequenceNumber &&
                timestamp == that.timestamp &&
                version == that.version &&
                flags == that.flags &&
                operationId.equals(that.operationId) &&
                commandType == that.commandType &&
                Arrays.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(sequenceNumber, operationId, timestamp, commandType, version, flags);
        result = 31 * result + Arrays.hashCode(payload);
        return result;
    }
}
