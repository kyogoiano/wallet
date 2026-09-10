package br.com.wallet.unit.edge;

import br.com.wallet.edge.api.CommandType;
import br.com.wallet.edge.internal.journal.CorruptedJournalException;
import br.com.wallet.edge.internal.journal.segmented.BinaryRecordCodec;
import br.com.wallet.edge.internal.journal.segmented.JournalRecord;
import br.com.wallet.edge.internal.journal.segmented.SegmentHeader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BinaryRecordCodec & SegmentHeader Unit Tests (TASK-1.1, REQ-EDG-007)")
class BinaryRecordCodecTest {

    private final BinaryRecordCodec codec = new BinaryRecordCodec();

    @Nested
    @DisplayName("1. SegmentHeader Tests (32-Byte Header)")
    class SegmentHeaderTests {

        @Test
        @DisplayName("Should encode and decode SegmentHeader with exact 32 bytes and valid CRC32C")
        void shouldEncodeAndDecodeValidSegmentHeader() {
            long segmentId = 42L;
            SegmentHeader header = SegmentHeader.create(segmentId);

            assertThat(SegmentHeader.HEADER_SIZE).isEqualTo(32);
            assertThat(header.segmentId()).isEqualTo(segmentId);
            assertThat(header.magic()).isEqualTo(SegmentHeader.MAGIC);
            assertThat(header.version()).isEqualTo((short) 1);

            ByteBuffer buffer = ByteBuffer.allocate(SegmentHeader.HEADER_SIZE);
            header.writeTo(buffer);
            buffer.flip();

            assertThat(buffer.remaining()).isEqualTo(32);

            SegmentHeader decoded = SegmentHeader.readFrom(buffer);
            assertThat(decoded.segmentId()).isEqualTo(segmentId);
            assertThat(decoded.magic()).isEqualTo(header.magic());
            assertThat(decoded.version()).isEqualTo(header.version());
            assertThat(decoded.createdAt()).isEqualTo(header.createdAt());
            assertThat(decoded.headerCrc32c()).isEqualTo(header.headerCrc32c());
        }

        @Test
        @DisplayName("Should reject SegmentHeader with corrupted magic")
        void shouldRejectCorruptedMagicInSegmentHeader() {
            ByteBuffer buffer = ByteBuffer.allocate(SegmentHeader.HEADER_SIZE);
            buffer.putInt(0xDEADBEEF); // Bad magic
            buffer.putShort((short) 1);
            buffer.putShort((short) 0);
            buffer.putLong(1L);
            buffer.putLong(System.currentTimeMillis());
            buffer.putInt(12345);
            buffer.putInt(0); // padding
            buffer.flip();

            assertThatThrownBy(() -> SegmentHeader.readFrom(buffer))
                    .isInstanceOf(CorruptedJournalException.class)
                    .hasMessageContaining("Invalid segment magic");
        }

        @Test
        @DisplayName("Should reject SegmentHeader with corrupted CRC32C")
        void shouldRejectCorruptedCrcInSegmentHeader() {
            SegmentHeader header = SegmentHeader.create(99L);
            ByteBuffer buffer = ByteBuffer.allocate(SegmentHeader.HEADER_SIZE);
            header.writeTo(buffer);

            // Corrupt the CRC at offset 24
            buffer.putInt(24, ~header.headerCrc32c());
            buffer.flip();

            assertThatThrownBy(() -> SegmentHeader.readFrom(buffer))
                    .isInstanceOf(CorruptedJournalException.class)
                    .hasMessageContaining("Segment header CRC32C mismatch");
        }
    }

    @Nested
    @DisplayName("2. JournalRecord & BinaryRecordCodec Tests (54-Byte Record Header + Payload)")
    class RecordCodecTests {

        @Test
        @DisplayName("Header size constant must be exactly 54 bytes")
        void headerSizeMustBe54Bytes() {
            assertThat(BinaryRecordCodec.HEADER_SIZE).isEqualTo(54);
        }

        @ParameterizedTest
        @EnumSource(CommandType.class)
        @DisplayName("Should round-trip encode and decode JournalRecord for all CommandTypes")
        void shouldRoundTripEncodeAndDecodeRecord(CommandType commandType) {
            UUID opId = UUID.randomUUID();
            long seqNo = 1001L;
            long timestamp = System.currentTimeMillis();
            byte[] payload = "{\"amount\": 150.00, \"currency\": \"BRL\"}".getBytes(StandardCharsets.UTF_8);

            JournalRecord record = new JournalRecord(seqNo, opId, timestamp, commandType, payload);
            int expectedTotalSize = 54 + payload.length;

            assertThat(codec.calculateRecordSize(record)).isEqualTo(expectedTotalSize);

            ByteBuffer buffer = ByteBuffer.allocate(expectedTotalSize);
            codec.encode(record, buffer);
            buffer.flip();

            assertThat(buffer.remaining()).isEqualTo(expectedTotalSize);

            JournalRecord decoded = codec.decode(buffer);
            assertThat(decoded.sequenceNumber()).isEqualTo(seqNo);
            assertThat(decoded.operationId()).isEqualTo(opId);
            assertThat(decoded.timestamp()).isEqualTo(timestamp);
            assertThat(decoded.commandType()).isEqualTo(commandType);
            assertThat(decoded.payload()).isEqualTo(payload);
            assertThat(decoded.version()).isEqualTo((short) 1);
        }

        @Test
        @DisplayName("Should preserve UUID big-endian representation across encoding")
        void shouldPreserveUuidBigEndianRepresentation() {
            UUID opId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
            JournalRecord record = new JournalRecord(1L, opId, 1000L, CommandType.TRANSFER, new byte[0]);

            ByteBuffer buffer = ByteBuffer.allocate(54);
            codec.encode(record, buffer);
            buffer.flip();

            // OpId is at offset 16 (4 magic + 2 ver + 2 flags + 4 len + 4 crc + 8 seq = 24)
            // Wait: 0..3: magic(4), 4..5: ver(2), 6..7: flags(2), 8..11: len(4), 12..15: crc(4), 16..23: seq(8)
            // OpId starts at offset 24
            long mostSig = buffer.getLong(24);
            long leastSig = buffer.getLong(32);

            assertThat(mostSig).isEqualTo(opId.getMostSignificantBits());
            assertThat(leastSig).isEqualTo(opId.getLeastSignificantBits());
        }

        @Test
        @DisplayName("Should detect bit rot in payload and throw CorruptedJournalException")
        void shouldDetectBitRotInPayload() {
            byte[] payload = "{\"accountId\": \"ACC-123\", \"amount\": 500.00}".getBytes(StandardCharsets.UTF_8);
            JournalRecord record = new JournalRecord(1L, UUID.randomUUID(), System.currentTimeMillis(), CommandType.DEPOSIT, payload);

            ByteBuffer buffer = ByteBuffer.allocate(codec.calculateRecordSize(record));
            codec.encode(record, buffer);

            // Corrupt last byte of payload
            buffer.put(buffer.position() - 1, (byte) (buffer.get(buffer.position() - 1) ^ 0x01));
            buffer.flip();

            assertThatThrownBy(() -> codec.decode(buffer))
                    .isInstanceOf(CorruptedJournalException.class)
                    .hasMessageContaining("CRC32C checksum mismatch");
        }

        @Test
        @DisplayName("Should detect bit rot in sequenceNumber and throw CorruptedJournalException")
        void shouldDetectBitRotInSequenceNumber() {
            JournalRecord record = new JournalRecord(999L, UUID.randomUUID(), System.currentTimeMillis(), CommandType.WITHDRAW, "test".getBytes());

            ByteBuffer buffer = ByteBuffer.allocate(codec.calculateRecordSize(record));
            codec.encode(record, buffer);

            // SeqNo is at offset 16
            buffer.putLong(16, 888L);
            buffer.flip();

            assertThatThrownBy(() -> codec.decode(buffer))
                    .isInstanceOf(CorruptedJournalException.class)
                    .hasMessageContaining("CRC32C checksum mismatch");
        }

        @Test
        @DisplayName("Should reject record with invalid magic bytes")
        void shouldRejectRecordWithInvalidMagic() {
            JournalRecord record = new JournalRecord(1L, UUID.randomUUID(), System.currentTimeMillis(), CommandType.TRANSFER, new byte[0]);

            ByteBuffer buffer = ByteBuffer.allocate(codec.calculateRecordSize(record));
            codec.encode(record, buffer);

            buffer.putInt(0, 0x12345678); // Tamper magic
            buffer.flip();

            assertThatThrownBy(() -> codec.decode(buffer))
                    .isInstanceOf(CorruptedJournalException.class)
                    .hasMessageContaining("Invalid record magic");
        }

        @Test
        @DisplayName("Should reject record with total length smaller than HEADER_SIZE")
        void shouldRejectRecordWithInvalidLength() {
            ByteBuffer buffer = ByteBuffer.allocate(30);
            buffer.putInt(BinaryRecordCodec.MAGIC);
            buffer.putShort((short) 1);
            buffer.putShort((short) 0);
            buffer.putInt(30); // Invalid length < 54
            buffer.flip();

            assertThatThrownBy(() -> codec.decode(buffer))
                    .isInstanceOf(CorruptedJournalException.class)
                    .hasMessageContaining("Invalid record length");
        }

        @Test
        @DisplayName("Should reject record when buffer has fewer than 12 bytes")
        void shouldRejectRecordWithTruncatedPrefix() {
            ByteBuffer buffer = ByteBuffer.allocate(8);
            buffer.putInt(BinaryRecordCodec.MAGIC);
            buffer.putShort((short) 1);
            buffer.flip();

            assertThatThrownBy(() -> codec.decode(buffer))
                    .isInstanceOf(CorruptedJournalException.class)
                    .hasMessageContaining("Buffer requires at least 12 bytes");
        }

        @Test
        @DisplayName("Should encode and decode envelope-encrypted opaque ciphertext payloads (TASK-6.6, SPEC-000.10)")
        void shouldSupportEnvelopeEncryptedCiphertextPayload() {
            UUID operationId = UUID.randomUUID();
            // Simulates envelope-encrypted payload: 12-byte IV + AES-GCM ciphertext + 16-byte Auth Tag
            byte[] opaqueCiphertext = new byte[256];
            java.util.concurrent.ThreadLocalRandom.current().nextBytes(opaqueCiphertext);

            JournalRecord encryptedRecord = new JournalRecord(
                    999L,
                    operationId,
                    System.currentTimeMillis(),
                    CommandType.TRANSFER,
                    opaqueCiphertext
            );

            int expectedTotalSize = codec.calculateRecordSize(encryptedRecord);
            ByteBuffer buffer = ByteBuffer.allocate(expectedTotalSize);

            codec.encode(encryptedRecord, buffer);
            buffer.flip();
            JournalRecord decoded = codec.decode(buffer);

            assertThat(decoded.sequenceNumber()).isEqualTo(999L);
            assertThat(decoded.operationId()).isEqualTo(operationId);
            assertThat(decoded.commandType()).isEqualTo(CommandType.TRANSFER);
            assertThat(decoded.payload()).isEqualTo(opaqueCiphertext);
        }
    }

    @Nested
    @DisplayName("3. CommandType Mapping Tests")
    class CommandTypeTests {

        @Test
        @DisplayName("CommandType codes must map correctly to 1, 2, 3")
        void commandTypeCodesMustMatchSpec() {
            assertThat(CommandType.TRANSFER.code()).isEqualTo((short) 1);
            assertThat(CommandType.DEPOSIT.code()).isEqualTo((short) 2);
            assertThat(CommandType.WITHDRAW.code()).isEqualTo((short) 3);

            assertThat(CommandType.fromCode((short) 1)).isEqualTo(CommandType.TRANSFER);
            assertThat(CommandType.fromCode((short) 2)).isEqualTo(CommandType.DEPOSIT);
            assertThat(CommandType.fromCode((short) 3)).isEqualTo(CommandType.WITHDRAW);
        }

        @Test
        @DisplayName("Unknown CommandType code must throw IllegalArgumentException")
        void unknownCommandTypeCodeMustThrow() {
            assertThatThrownBy(() -> CommandType.fromCode((short) 99))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown CommandType code: 99");
        }
    }
}
