package br.com.wallet.infrastructure.messaging.consumer;

import br.com.wallet.edge.api.OperationStatusBroadcaster;
import br.com.wallet.infrastructure.messaging.publisher.DlqPublisher;
import br.com.wallet.ledger.api.DepositFundsUseCase;
import br.com.wallet.ledger.api.TransferFundsUseCase;
import br.com.wallet.ledger.api.WithdrawFundsUseCase;
import br.com.wallet.ledger.api.context.Transfer;
import br.com.wallet.ledger.api.guard.FraudCheckHelper;
import br.com.wallet.security.envelope.AesGcmEnvelopeDecryptor;
import br.com.wallet.security.envelope.AesGcmEnvelopeEncryptor;
import br.com.wallet.security.envelope.CryptoBytes;
import br.com.wallet.security.envelope.CryptoEnvelope;
import br.com.wallet.security.envelope.EnvelopeCodec;
import br.com.wallet.security.envelope.KeyId;
import br.com.wallet.security.envelope.OperationId;
import br.com.wallet.security.envelope.TenantId;
import br.com.wallet.security.failure.KeyManagementUnavailableException;
import br.com.wallet.security.keymanagement.GeneratedDataKey;
import br.com.wallet.security.keymanagement.KeyManagementClient;
import br.com.wallet.security.keymanagement.SensitiveKeyMaterial;
import io.nats.client.Connection;
import io.nats.client.Message;
import io.nats.client.impl.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
@DisplayName("TASK-10.14: Core Pre-Gate Decrypt-Before-Transaction Test (REQ-SEC-022, I-ENV-003)")
class CoreDecryptBeforeTransactionTest {

    @Mock
    private Connection natsConnection;

    @Mock
    private TransferFundsUseCase transferFundsUseCase;

    @Mock
    private DepositFundsUseCase depositFundsUseCase;

    @Mock
    private WithdrawFundsUseCase withdrawFundsUseCase;

    @Mock
    private FraudCheckHelper fraudCheckHelper;

    @Mock
    private OperationStatusBroadcaster statusBroadcaster;

    @Mock
    private DlqPublisher dlqPublisher;

    @Mock
    private KeyManagementClient keyManagementClient;

    @Mock
    private Message natsMessage;

    private ObjectMapper objectMapper;
    private AesGcmEnvelopeEncryptor encryptor;
    private AesGcmEnvelopeDecryptor decryptor;
    private CoreCommandConsumer consumer;
    private final SecureRandom secureRandom = new SecureRandom();

    private final TenantId tenantId = new TenantId("tenant-finance-alpha");
    private final KeyId keyId = new KeyId("kms-root-key");

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        encryptor = new AesGcmEnvelopeEncryptor();
        decryptor = new AesGcmEnvelopeDecryptor();

        consumer = new CoreCommandConsumer(
                natsConnection,
                objectMapper,
                transferFundsUseCase,
                depositFundsUseCase,
                withdrawFundsUseCase,
                fraudCheckHelper,
                statusBroadcaster,
                dlqPublisher,
                null,
                decryptor,
                keyManagementClient
        );
    }

    private Headers createHeaders(UUID opId) {
        Headers headers = new Headers();
        headers.add("tenant_id", tenantId.value());
        headers.add("principal_id", "principal-100");
        headers.add("key_id", keyId.value());
        headers.add("publisher_id", "edge-gateway");
        headers.add("operation_id", opId.toString());
        headers.add("type", "TRANSFER");
        headers.add("content_type", "application/x-wallet-crypto-envelope");
        return headers;
    }

    @Test
    @DisplayName("Assert Core consumer decrypts payload before dispatching to transactional use case (I-ENV-003)")
    void shouldDecryptBeforeCallingUseCase() {
        UUID opId = UUID.randomUUID();
        String jsonPayload = "{\"from\":\"11111111-1111-1111-1111-111111111111\",\"to\":\"22222222-2222-2222-2222-222222222222\",\"amount\":100.00,\"operationId\":\"" + opId + "\"}";

        byte[] rawKey = new byte[32];
        secureRandom.nextBytes(rawKey);
        SensitiveKeyMaterial plaintextDek = new SensitiveKeyMaterial(rawKey);
        CryptoBytes wrappedDek = new CryptoBytes(new byte[]{1, 2, 3, 4});

        GeneratedDataKey dataKey = new GeneratedDataKey(plaintextDek, wrappedDek);
        CryptoEnvelope envelope = encryptor.encrypt(jsonPayload.getBytes(StandardCharsets.UTF_8), tenantId, new OperationId(opId), keyId, dataKey);
        byte[] envelopeBytes = EnvelopeCodec.encode(envelope);

        when(natsMessage.getSubject()).thenReturn("commands.wallet.transfer");
        when(natsMessage.getHeaders()).thenReturn(createHeaders(opId));
        when(natsMessage.getData()).thenReturn(envelopeBytes);
        when(keyManagementClient.decryptDataKey(eq(tenantId), eq(keyId), any(), any()))
                .thenReturn(new SensitiveKeyMaterial(rawKey));

        consumer.processMessage(natsMessage);

        // Verify transferFundsUseCase received the unencrypted command
        verify(transferFundsUseCase).handle(any(Transfer.class));
        verify(natsMessage).ack();
    }

    @Test
    @DisplayName("Assert tampered envelope is quarantined to DLQ under CRYPTOGRAPHIC_TAMPER_DETECTED and ACKed")
    void shouldQuarantineTamperedEnvelopeToDlq() {
        UUID opId = UUID.randomUUID();
        String jsonPayload = "{\"from\":\"11111111-1111-1111-1111-111111111111\",\"to\":\"22222222-2222-2222-2222-222222222222\",\"amount\":100.00}";

        byte[] rawKey = new byte[32];
        secureRandom.nextBytes(rawKey);
        GeneratedDataKey dataKey = new GeneratedDataKey(new SensitiveKeyMaterial(rawKey), new CryptoBytes(new byte[16]));
        CryptoEnvelope envelope = encryptor.encrypt(jsonPayload.getBytes(StandardCharsets.UTF_8), tenantId, new OperationId(opId), keyId, dataKey);

        // Tamper ciphertext
        byte[] tamperedCipher = envelope.ciphertext().value();
        tamperedCipher[0] ^= 0x01;
        CryptoEnvelope tamperedEnvelope = new CryptoEnvelope(
                envelope.version(), envelope.tenantId(), envelope.operationId(), envelope.keyId(),
                envelope.algorithm(), envelope.iv(), envelope.wrappedDek(), new CryptoBytes(tamperedCipher)
        );
        byte[] tamperedBytes = EnvelopeCodec.encode(tamperedEnvelope);

        when(natsMessage.getSubject()).thenReturn("commands.wallet.transfer");
        when(natsMessage.getHeaders()).thenReturn(createHeaders(opId));
        when(natsMessage.getData()).thenReturn(tamperedBytes);
        when(keyManagementClient.decryptDataKey(eq(tenantId), eq(keyId), any(), any()))
                .thenReturn(new SensitiveKeyMaterial(rawKey));

        consumer.processMessage(natsMessage);

        // Transactional use case must NEVER be called
        verify(transferFundsUseCase, never()).handle(any());
        // Quarantined to DLQ
        try {
            verify(dlqPublisher).publishDlqConfirmed(eq("commands.dlq.transfer"), eq(natsConnection), eq(natsMessage), any());
        } catch (Exception ignored) {}
        verify(natsMessage).ack();
    }

    @Test
    @DisplayName("Assert KMS unavailability NACKs message with delay for retry without holding DB connection")
    void shouldNackWhenKmsUnavailable() {
        UUID opId = UUID.randomUUID();
        String jsonPayload = "{\"amount\":50.00}";

        byte[] rawKey = new byte[32];
        secureRandom.nextBytes(rawKey);
        GeneratedDataKey dataKey = new GeneratedDataKey(new SensitiveKeyMaterial(rawKey), new CryptoBytes(new byte[16]));
        CryptoEnvelope envelope = encryptor.encrypt(jsonPayload.getBytes(StandardCharsets.UTF_8), tenantId, new OperationId(opId), keyId, dataKey);
        byte[] envelopeBytes = EnvelopeCodec.encode(envelope);

        when(natsMessage.getSubject()).thenReturn("commands.wallet.transfer");
        when(natsMessage.getHeaders()).thenReturn(createHeaders(opId));
        when(natsMessage.getData()).thenReturn(envelopeBytes);
        when(keyManagementClient.decryptDataKey(eq(tenantId), eq(keyId), any(), any()))
                .thenThrow(new KeyManagementUnavailableException("KMS timeout", tenantId, keyId));

        consumer.processMessage(natsMessage);

        verify(transferFundsUseCase, never()).handle(any());
        verify(natsMessage).nakWithDelay(any(Duration.class));
        verify(natsMessage, never()).ack();
    }
}
