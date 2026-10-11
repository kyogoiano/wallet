package br.com.wallet.edge.internal.journal.segmented;

import br.com.wallet.edge.api.CommandType;
import br.com.wallet.edge.internal.journal.CorruptedJournalException;

import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.zip.CRC32C;

/**
 * High-performance binary encoder and decoder for segmented journal records.
 * Framing specification (PLAN-000.9 Section 4.2):
 * [Magic: 4B (0x57414C52)][Version: 2B][Flags: 2B][Length: 4B][CRC32C: 4B]
 * [SeqNo: 8B][OpId: 16B][Timestamp: 8B][CmdType: 2B][PayloadLen: 4B][Payload: NB]
 * <p>
 * Header size is exactly 54 bytes.
 * CRC32C covers bytes from offset 16 (SequenceNumber) to the end of the payload.
 */
public class BinaryRecordCodec {

    public static final int HEADER_SIZE = 54;
    public static final int MAGIC = 0x57414C52; // "WALR" (Wallet Record)
    public static final short CURRENT_VERSION = 1;

    public int calculateRecordSize(JournalRecord record) {
        return HEADER_SIZE + record.payload().length;
    }

    public void encode(JournalRecord record, ByteBuffer buffer) {
        int totalLength = calculateRecordSize(record);
        if (buffer.remaining() < totalLength) {
            throw new IllegalArgumentException("Buffer requires at least " + totalLength + " bytes, available: " + buffer.remaining());
        }

        int startPos = buffer.position();

        // 1. Header prefix (0..15)
        buffer.putInt(MAGIC);
        buffer.putShort(record.version());
        buffer.putShort(record.flags());
        buffer.putInt(totalLength);
        
        int crcPos = buffer.position();
        buffer.putInt(0); // Placeholder for CRC32C

        // 2. Protected body (16..end)
        int protectedDataStart = buffer.position();
        buffer.putLong(record.sequenceNumber());
        buffer.putLong(record.operationId().getMostSignificantBits());
        buffer.putLong(record.operationId().getLeastSignificantBits());
        buffer.putLong(record.timestamp());
        buffer.putShort(record.commandType().code());
        buffer.putInt(record.payload().length);
        buffer.put(record.payload());
        int endPos = buffer.position();

        // 3. Compute Castagnoli CRC32C overprotected body
        CRC32C crc = new CRC32C();
        ByteBuffer protectedSlice = buffer.duplicate();
        protectedSlice.position(protectedDataStart);
        protectedSlice.limit(endPos);
        crc.update(protectedSlice);
        int computedCrc = (int) crc.getValue();

        // 4. Backfill CRC
        buffer.putInt(crcPos, computedCrc);
    }

    public JournalRecord decode(ByteBuffer buffer) {
        if (buffer.remaining() < 12) {
            throw new CorruptedJournalException("Buffer requires at least 12 bytes for record prefix, available: " + buffer.remaining());
        }

        int startPos = buffer.position();

        int magic = buffer.getInt();
        if (magic != MAGIC) {
            throw new CorruptedJournalException("Invalid record magic: 0x" + Integer.toHexString(magic));
        }

        short version = buffer.getShort();
        short flags = buffer.getShort();
        int totalLength = buffer.getInt();

        if (totalLength < HEADER_SIZE) {
            throw new CorruptedJournalException("Invalid record length: " + totalLength + " (minimum is " + HEADER_SIZE + ")");
        }

        if (buffer.remaining() < totalLength - 12) {
            throw new CorruptedJournalException("Buffer underflow: expected " + totalLength + " bytes, available: " + (buffer.remaining() + 12));
        }

        int expectedCrc = buffer.getInt();
        int protectedDataStart = buffer.position();
        int protectedDataLength = totalLength - 16;

        // Verify CRC32C over protected data
        CRC32C crc = new CRC32C();
        ByteBuffer protectedSlice = buffer.duplicate();
        protectedSlice.position(protectedDataStart);
        protectedSlice.limit(protectedDataStart + protectedDataLength);
        crc.update(protectedSlice);
        int computedCrc = (int) crc.getValue();

        if (computedCrc != expectedCrc) {
            throw new CorruptedJournalException("CRC32C checksum mismatch: expected=" + expectedCrc + ", calculated=" + computedCrc);
        }

        // Decode fields
        long seqNo = buffer.getLong();
        long mostSig = buffer.getLong();
        long leastSig = buffer.getLong();
        UUID opId = new UUID(mostSig, leastSig);
        long timestamp = buffer.getLong();
        short cmdTypeCode = buffer.getShort();
        CommandType cmdType = CommandType.fromCode(cmdTypeCode);
        int payloadLen = buffer.getInt();

        if (payloadLen != totalLength - HEADER_SIZE) {
            throw new CorruptedJournalException("Mismatched payload length: " + payloadLen + " (expected " + (totalLength - HEADER_SIZE) + ")");
        }

        byte[] payload = new byte[payloadLen];
        buffer.get(payload);

        return new JournalRecord(seqNo, opId, timestamp, cmdType, payload, version, flags);
    }
}
