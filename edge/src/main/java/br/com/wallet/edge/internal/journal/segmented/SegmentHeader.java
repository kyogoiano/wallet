package br.com.wallet.edge.internal.journal.segmented;

import br.com.wallet.edge.internal.journal.CorruptedJournalException;

import java.nio.ByteBuffer;
import java.util.zip.CRC32C;

/**
 * 32-Byte Header preallocated at the beginning of each 64MB journal segment file.
 * Format:
 * [Magic: 4B (0x57414C4A)][Version: 2B (0x0001)][Flags: 2B (0x0000)][SegmentId: 8B]
 * [CreatedAt: 8B][HeaderCRC32C: 4B][Padding: 4B]
 */
public record SegmentHeader(
        int magic,
        short version,
        short flags,
        long segmentId,
        long createdAt,
        int headerCrc32c
) {
    public static final int HEADER_SIZE = 32;
    public static final int MAGIC = 0x57414C4A; // "WALJ"
    public static final short CURRENT_VERSION = 1;

    public static SegmentHeader create(long segmentId) {
        long now = System.currentTimeMillis();
        int crc = computeHeaderCrc(MAGIC, CURRENT_VERSION, (short) 0, segmentId, now);
        return new SegmentHeader(MAGIC, CURRENT_VERSION, (short) 0, segmentId, now, crc);
    }

    public void writeTo(ByteBuffer buffer) {
        if (buffer.remaining() < HEADER_SIZE) {
            throw new IllegalArgumentException("Buffer requires at least " + HEADER_SIZE + " bytes, available: " + buffer.remaining());
        }
        int startPos = buffer.position();
        buffer.putInt(magic);
        buffer.putShort(version);
        buffer.putShort(flags);
        buffer.putLong(segmentId);
        buffer.putLong(createdAt);
        buffer.putInt(headerCrc32c);
        buffer.putInt(0); // 4 bytes padding
    }

    public static SegmentHeader readFrom(ByteBuffer buffer) {
        if (buffer.remaining() < HEADER_SIZE) {
            throw new IllegalArgumentException("Buffer requires at least " + HEADER_SIZE + " bytes, available: " + buffer.remaining());
        }
        int magic = buffer.getInt();
        if (magic != MAGIC) {
            throw new CorruptedJournalException("Invalid segment magic: 0x" + Integer.toHexString(magic));
        }
        short version = buffer.getShort();
        short flags = buffer.getShort();
        long segmentId = buffer.getLong();
        long createdAt = buffer.getLong();
        int expectedCrc = buffer.getInt();
        int padding = buffer.getInt(); // 4 bytes padding

        int calculatedCrc = computeHeaderCrc(magic, version, flags, segmentId, createdAt);
        if (calculatedCrc != expectedCrc) {
            throw new CorruptedJournalException("Segment header CRC32C mismatch: expected=" + expectedCrc + ", calculated=" + calculatedCrc);
        }

        return new SegmentHeader(magic, version, flags, segmentId, createdAt, expectedCrc);
    }

    private static int computeHeaderCrc(int magic, short version, short flags, long segmentId, long createdAt) {
        ByteBuffer buf = ByteBuffer.allocate(24);
        buf.putInt(magic);
        buf.putShort(version);
        buf.putShort(flags);
        buf.putLong(segmentId);
        buf.putLong(createdAt);
        buf.flip();

        CRC32C crc = new CRC32C();
        crc.update(buf);
        return (int) crc.getValue();
    }
}
