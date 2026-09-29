package br.com.wallet.edge.internal.journal;

import br.com.wallet.edge.api.CommandType;
import br.com.wallet.edge.internal.journal.segmented.BinaryRecordCodec;
import br.com.wallet.edge.internal.journal.segmented.JournalRecord;
import br.com.wallet.edge.internal.journal.segmented.SegmentedFileJournal;
import br.com.wallet.security.envelope.AesGcmEnvelopeDecryptor;
import br.com.wallet.security.envelope.AesGcmEnvelopeEncryptor;
import br.com.wallet.security.envelope.CryptoBytes;
import br.com.wallet.security.envelope.CryptoEnvelope;
import br.com.wallet.security.envelope.EnvelopeCodec;
import br.com.wallet.security.envelope.KeyId;
import br.com.wallet.security.envelope.OperationId;
import br.com.wallet.security.envelope.TenantId;
import br.com.wallet.security.keymanagement.GeneratedDataKey;
import br.com.wallet.security.keymanagement.SensitiveKeyMaterial;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TASK-10.12: Zero Plaintext Spool Journal Persistence Test (TASK-6.6, I-ENV-001)")
class ZeroPlaintextSpoolJournalTest {

    @TempDir
    Path tempDir;

    private SegmentedFileJournal journal;
    private final AesGcmEnvelopeEncryptor encryptor = new AesGcmEnvelopeEncryptor();
    private final AesGcmEnvelopeDecryptor decryptor = new AesGcmEnvelopeDecryptor();
    private final BinaryRecordCodec codec = new BinaryRecordCodec();
    private final SecureRandom secureRandom = new SecureRandom();

    @BeforeEach
    void setUp() throws IOException {
        journal = new SegmentedFileJournal(
                tempDir,
                1024 * 1024L, // 1MB segment size
                10 * 1024 * 1024L, // 10MB capacity
                10,
                5
        );
        journal.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (journal != null) {
            journal.close();
        }
    }

    @Test
    @DisplayName("Assert spool journal file contains zero plaintext financial data (I-ENV-001)")
    void shouldPersistEncryptedEnvelopeWithZeroPlaintextOnDisk() throws Exception {
        TenantId tenantId = new TenantId("tenant-private-bank");
        UUID opUuid = UUID.randomUUID();
        OperationId opId = new OperationId(opUuid);
        KeyId keyId = new KeyId("kms-appliance-key-1");

        String sensitivePayload = "{\"fromAccount\":\"1111-2222-3333-4444\",\"toAccount\":\"5555-6666-7777-8888\",\"amount\":99999.99,\"owner\":\"Alice Smith\"}";
        byte[] plaintextBytes = sensitivePayload.getBytes(StandardCharsets.UTF_8);

        // Generate DEK and encrypt payload
        byte[] rawKey = new byte[32];
        secureRandom.nextBytes(rawKey);
        byte[] rawWrapped = new byte[48];
        secureRandom.nextBytes(rawWrapped);

        GeneratedDataKey dataKey = new GeneratedDataKey(new SensitiveKeyMaterial(rawKey), new CryptoBytes(rawWrapped));
        CryptoEnvelope envelope = encryptor.encrypt(plaintextBytes, tenantId, opId, keyId, dataKey);

        byte[] envelopeBytes = EnvelopeCodec.encode(envelope);

        // Append encrypted payload to journal
        JournalRecord record = journal.append(CommandType.TRANSFER, opUuid, envelopeBytes).get(5, TimeUnit.SECONDS);
        assertThat(record).isNotNull();

        // Flush and close journal to ensure all bytes are synced to disk
        journal.close();

        // Locate segment file
        Path segmentFile;
        try (Stream<Path> stream = Files.list(tempDir)) {
            segmentFile = stream.filter(p -> p.getFileName().toString().endsWith(".wal")).findFirst()
                    .orElseThrow(() -> new IllegalStateException("No .wal file found"));
        }

        byte[] allSegmentBytes = Files.readAllBytes(segmentFile);
        String segmentString = new String(allSegmentBytes, StandardCharsets.ISO_8859_1);

        // Assert ZERO occurrences of sensitive plaintext financial data anywhere in the .wal file
        assertThat(segmentString).doesNotContain("1111-2222-3333-4444");
        assertThat(segmentString).doesNotContain("5555-6666-7777-8888");
        assertThat(segmentString).doesNotContain("99999.99");
        assertThat(segmentString).doesNotContain("Alice Smith");
        assertThat(segmentString).doesNotContain("fromAccount");
        assertThat(segmentString).doesNotContain("toAccount");

        // Verify that the record can be recovered and decrypted cleanly
        try (FileChannel channel = FileChannel.open(segmentFile, StandardOpenOption.READ)) {
            ByteBuffer buf = ByteBuffer.allocate((int) Files.size(segmentFile));
            channel.read(buf);
            buf.flip();

            // Skip 32B segment header
            buf.position(32);
            JournalRecord recoveredRecord = codec.decode(buf);

            assertThat(recoveredRecord.operationId()).isEqualTo(opUuid);
            assertThat(recoveredRecord.commandType()).isEqualTo(CommandType.TRANSFER);

            CryptoEnvelope recoveredEnvelope = EnvelopeCodec.decode(recoveredRecord.payload());
            byte[] decryptedBytes = decryptor.decrypt(recoveredEnvelope, dataKey.plaintextDek());

            assertThat(new String(decryptedBytes, StandardCharsets.UTF_8)).isEqualTo(sensitivePayload);
        }
    }
}
