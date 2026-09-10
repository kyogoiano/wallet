package br.com.wallet.unit.infrastructure.messaging;

import br.com.wallet.edge.api.CommandEnvelope;
import br.com.wallet.edge.api.CommandType;
import br.com.wallet.infrastructure.messaging.publisher.NatsEdgeCommandPublisher;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.Message;
import io.nats.client.PublishOptions;
import io.nats.client.api.PublishAck;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NatsEdgeCommandPublisher Unit Tests (REQ-EDG-021 & I-DEDUP-001)")
class NatsEdgeCommandPublisherTest {

    @Mock
    private Connection connection;

    @Mock
    private JetStream jetStream;

    @Mock
    private PublishAck publishAck;

    private ObjectMapper objectMapper;
    private NatsEdgeCommandPublisher publisher;

    @BeforeEach
    void setUp() throws IOException {
        objectMapper = new ObjectMapper();
        when(connection.jetStream()).thenReturn(jetStream);
        publisher = new NatsEdgeCommandPublisher(connection, objectMapper);
    }

    @Test
    @DisplayName("Should publish transfer command to commands.wallet.transfer with Nats-Msg-Id and trace headers")
    void shouldPublishTransferCommandWithNatsMsgIdAndTraceHeaders() throws Exception {
        UUID opId = UUID.randomUUID();
        String payload = """
                {"from":"%s","to":"%s","amount":150.00}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());
        CommandEnvelope envelope = CommandEnvelope.create(opId, CommandType.TRANSFER, payload, "192.168.1.50", "tenant-1");

        CompletableFuture<PublishAck> ackFuture = CompletableFuture.completedFuture(publishAck);
        when(jetStream.publishAsync(any(Message.class), any(PublishOptions.class))).thenReturn(ackFuture);

        CompletableFuture<Void> result = publisher.publish(envelope);
        result.get();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        ArgumentCaptor<PublishOptions> optionsCaptor = ArgumentCaptor.forClass(PublishOptions.class);
        verify(jetStream).publishAsync(messageCaptor.capture(), optionsCaptor.capture());

        Message publishedMessage = messageCaptor.getValue();
        assertThat(publishedMessage.getSubject()).isEqualTo("commands.wallet.transfer");
        assertThat(publishedMessage.getHeaders().getFirst("Nats-Msg-Id")).isEqualTo(opId.toString());
        assertThat(publishedMessage.getHeaders().getFirst("operation_id")).isEqualTo(opId.toString());
        assertThat(publishedMessage.getHeaders().getFirst("type")).isEqualTo("TRANSFER");
        assertThat(publishedMessage.getHeaders().getFirst("client_ip")).isEqualTo("192.168.1.50");
        assertThat(publishedMessage.getHeaders().getFirst("tenant_id")).isEqualTo("tenant-1");

        // Verify enriched payload contains operationId
        String publishedBody = new String(publishedMessage.getData(), StandardCharsets.UTF_8);
        JsonNode node = objectMapper.readTree(publishedBody);
        assertThat(node.get("operationId").asString()).isEqualTo(opId.toString());

        PublishOptions options = optionsCaptor.getValue();
        assertThat(options.getExpectedStream()).isEqualTo("commands");
    }

    @Test
    @DisplayName("Should publish deposit command to commands.wallet.deposit")
    void shouldPublishDepositCommand() throws Exception {
        UUID opId = UUID.randomUUID();
        String payload = """
                {"walletId":"%s","amount":50.00}
                """.formatted(UUID.randomUUID());
        CommandEnvelope envelope = CommandEnvelope.create(opId, CommandType.DEPOSIT, payload, "127.0.0.1", "default");

        when(jetStream.publishAsync(any(Message.class), any(PublishOptions.class)))
                .thenReturn(CompletableFuture.completedFuture(publishAck));

        publisher.publish(envelope).get();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(jetStream).publishAsync(messageCaptor.capture(), any());
        assertThat(messageCaptor.getValue().getSubject()).isEqualTo("commands.wallet.deposit");
    }

    @Test
    @DisplayName("Should publish withdraw command to commands.wallet.withdraw")
    void shouldPublishWithdrawCommand() throws Exception {
        UUID opId = UUID.randomUUID();
        String payload = """
                {"walletId":"%s","amount":75.00}
                """.formatted(UUID.randomUUID());
        CommandEnvelope envelope = CommandEnvelope.create(opId, CommandType.WITHDRAW, payload, "127.0.0.1", "default");

        when(jetStream.publishAsync(any(Message.class), any(PublishOptions.class)))
                .thenReturn(CompletableFuture.completedFuture(publishAck));

        publisher.publish(envelope).get();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(jetStream).publishAsync(messageCaptor.capture(), any());
        assertThat(messageCaptor.getValue().getSubject()).isEqualTo("commands.wallet.withdraw");
    }

    @Test
    @DisplayName("Should fail returned future when JetStream publishAsync completes exceptionally")
    void shouldFailWhenJetStreamPublishFails() {
        UUID opId = UUID.randomUUID();
        CommandEnvelope envelope = CommandEnvelope.create(opId, CommandType.TRANSFER, "{}", "127.0.0.1");

        CompletableFuture<PublishAck> failedAck = CompletableFuture.failedFuture(new IOException("NATS connection lost"));
        when(jetStream.publishAsync(any(Message.class), any(PublishOptions.class))).thenReturn(failedAck);

        CompletableFuture<Void> result = publisher.publish(envelope);
        assertThatThrownBy(result::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IOException.class);
    }
}
