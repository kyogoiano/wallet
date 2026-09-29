package br.com.wallet.unit.edge;

import br.com.wallet.edge.api.CommandEnvelope;
import br.com.wallet.edge.api.CommandType;
import br.com.wallet.edge.internal.publisher.NatsEdgeCommandPublisher;
import br.com.wallet.edge.testsupport.InMemoryKeyManagementClient;
import br.com.wallet.security.envelope.AesGcmEnvelopeDecryptor;
import br.com.wallet.security.envelope.AesGcmEnvelopeEncryptor;
import br.com.wallet.security.envelope.CryptoEnvelope;
import br.com.wallet.security.envelope.EnvelopeCodec;
import br.com.wallet.security.keymanagement.KeyContext;
import br.com.wallet.security.keymanagement.SensitiveKeyMaterial;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.PublishOptions;
import io.nats.client.api.PublishAck;
import io.nats.client.impl.NatsMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TASK-10.13: NatsEdgeCommandPublisher Opaque CryptoEnvelope Publishing Test (REQ-SEC-021, JEP 527)")
class NatsEdgeCommandPublisherSecurityTest {

    @Mock
    private Connection connection;

    @Mock
    private JetStream jetStream;

    @Mock
    private PublishAck publishAck;

    private ObjectMapper objectMapper;
    private InMemoryKeyManagementClient kmsClient;
    private AesGcmEnvelopeEncryptor encryptor;
    private AesGcmEnvelopeDecryptor decryptor;
    private NatsEdgeCommandPublisher publisher;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        kmsClient = new InMemoryKeyManagementClient();
        encryptor = new AesGcmEnvelopeEncryptor();
        decryptor = new AesGcmEnvelopeDecryptor();
        publisher = new NatsEdgeCommandPublisher(connection, objectMapper, encryptor, kmsClient);
    }

    @Test
    @DisplayName("Assert published message payload is an opaque CryptoEnvelope with zero plaintext financial data")
    void shouldPublishOpaqueCryptoEnvelope() throws Exception {
        when(connection.jetStream()).thenReturn(jetStream);
        when(jetStream.publishAsync(any(NatsMessage.class), any(PublishOptions.class)))
                .thenReturn(CompletableFuture.completedFuture(publishAck));

        UUID opId = UUID.randomUUID();
        String sensitiveJson = "{\"fromAccount\":\"ACC-111\",\"toAccount\":\"ACC-222\",\"amount\":750.50}";

        CommandEnvelope command = CommandEnvelope.create(
                opId,
                CommandType.TRANSFER,
                sensitiveJson,
                "10.0.0.1",
                "tenant-finance-1",
                "principal-x",
                "key-kms-01"
        );

        publisher.publish(command).join();

        ArgumentCaptor<NatsMessage> messageCaptor = ArgumentCaptor.forClass(NatsMessage.class);
        verify(jetStream).publishAsync(messageCaptor.capture(), any(PublishOptions.class));

        NatsMessage publishedMessage = messageCaptor.getValue();
        byte[] payloadData = publishedMessage.getData();

        // 1. Assert payload is NOT plaintext JSON
        String payloadString = new String(payloadData, StandardCharsets.ISO_8859_1);
        assertThat(payloadString).doesNotContain("ACC-111");
        assertThat(payloadString).doesNotContain("ACC-222");
        assertThat(payloadString).doesNotContain("750.50");

        // 2. Assert headers identify envelope
        assertThat(publishedMessage.getHeaders().getFirst("content_type"))
                .isEqualTo("application/x-wallet-crypto-envelope");

        // 3. Assert consumer can decode envelope and decrypt original payload using KMS
        CryptoEnvelope decodedEnvelope = EnvelopeCodec.decode(payloadData);
        assertThat(decodedEnvelope.tenantId().value()).isEqualTo("tenant-finance-1");
        assertThat(decodedEnvelope.operationId().value()).isEqualTo(opId);

        try (SensitiveKeyMaterial plaintextDek = kmsClient.decryptDataKey(
                decodedEnvelope.tenantId(),
                decodedEnvelope.keyId(),
                decodedEnvelope.wrappedDek(),
                KeyContext.forTenant(decodedEnvelope.tenantId()))) {

            byte[] decryptedBytes = decryptor.decrypt(decodedEnvelope, plaintextDek);
            String recoveredJson = new String(decryptedBytes, StandardCharsets.UTF_8);

            assertThat(recoveredJson).contains("ACC-111");
            assertThat(recoveredJson).contains("ACC-222");
            assertThat(recoveredJson).contains("750.5");
        }
    }
}
