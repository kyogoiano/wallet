package br.com.wallet.unit.infrastructure.messaging;

import br.com.wallet.core.context.OperationOrigin;
import br.com.wallet.ledger.api.context.Deposit;
import br.com.wallet.ledger.api.context.Transfer;
import br.com.wallet.ledger.api.context.Wallet;
import br.com.wallet.ledger.api.context.Withdraw;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Command Context Serialization & Deserialization Unit Tests")
class CommandSerializationTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private <T> T deserializePayload(byte[] data, Class<T> clazz) {
        JsonNode node = objectMapper.readTree(data);
        while (node != null && node.isString()) {
            try {
                node = objectMapper.readTree(node.asString());
            } catch (Exception e) {
                break;
            }
        }
        return objectMapper.treeToValue(node, clazz);
    }

    @Test
    @DisplayName("Should serialize and deserialize Withdraw command record with complete data")
    void shouldSerializeAndDeserializeWithdrawCommand() {
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("150.75");

        Withdraw original = new Withdraw(walletId, userId, amount, operationId, OperationOrigin.USER);

        byte[] bytes = objectMapper.writeValueAsBytes(original);
        Withdraw deserialized = deserializePayload(bytes, Withdraw.class);

        assertThat(deserialized).isNotNull();
        assertThat(deserialized.walletId()).isEqualTo(walletId);
        assertThat(deserialized.userId()).isEqualTo(userId);
        assertThat(deserialized.amount()).isEqualByComparingTo(amount);
        assertThat(deserialized.operationId()).isEqualTo(operationId);
        assertThat(deserialized.origin()).isEqualTo(OperationOrigin.USER);

        // Verify traceTags() can be safely invoked without NPE
        assertThat(deserialized.traceTags())
                .containsEntry("wallet.id", walletId.toString())
                .containsEntry("operation.origin", "USER");
    }

    @Test
    @DisplayName("Should deserialize string-wrapped and unknown-field Withdraw command from DLQ replay")
    void shouldDeserializeStringWrappedWithdrawCommand() {
        String innerJson = """
                {"walletId":"0a35fb14-75ee-4125-943b-500893c30d33","userId":"2d0b175d-ee1c-41ab-9dda-a050ef29dfa8","amount":50,"operationId":"0a35fb14-75ee-4125-943b-500893c30d39","origin":"USER","sourceUserIdForFraudCheck":"2d0b175d-ee1c-41ab-9dda-a050ef29dfa8","targetUserIdForFraudCheck":null}
                """.trim();

        // Simulate string wrapping (e.g. from JSON serialization of String payload)
        byte[] stringWrappedPayload = objectMapper.writeValueAsBytes(innerJson);

        Withdraw deserialized = deserializePayload(stringWrappedPayload, Withdraw.class);

        assertThat(deserialized).isNotNull();
        assertThat(deserialized.walletId()).isEqualTo(UUID.fromString("0a35fb14-75ee-4125-943b-500893c30d33"));
        assertThat(deserialized.userId()).isEqualTo(UUID.fromString("2d0b175d-ee1c-41ab-9dda-a050ef29dfa8"));
        assertThat(deserialized.amount()).isEqualByComparingTo(new BigDecimal("50"));
        assertThat(deserialized.operationId()).isEqualTo(UUID.fromString("0a35fb14-75ee-4125-943b-500893c30d39"));
        assertThat(deserialized.origin()).isEqualTo(OperationOrigin.USER);
    }

    @Test
    @DisplayName("Should deserialize double-encoded JSON String payload for Deposit command")
    void shouldDeserializeDoubleEncodedDepositCommand() {
        String innerJson = """
                {"walletId":"0a35fb14-75ee-4125-943b-500893c30d33","userId":"2d0b175d-ee1c-41ab-9dda-a050ef29dfa8","amount":100000,"operationId":"0a35fb14-75ee-4125-943b-500893c30d32","origin":"USER","sourceUserIdForFraudCheck":"2d0b175d-ee1c-41ab-9dda-a050ef29dfa8","targetUserIdForFraudCheck":null}
                """.trim();

        byte[] stringWrappedPayload = objectMapper.writeValueAsBytes(innerJson);

        Deposit deserialized = deserializePayload(stringWrappedPayload, Deposit.class);

        assertThat(deserialized).isNotNull();
        assertThat(deserialized.walletId()).isEqualTo(UUID.fromString("0a35fb14-75ee-4125-943b-500893c30d33"));
        assertThat(deserialized.userId()).isEqualTo(UUID.fromString("2d0b175d-ee1c-41ab-9dda-a050ef29dfa8"));
        assertThat(deserialized.amount()).isEqualByComparingTo(new BigDecimal("100000"));
        assertThat(deserialized.operationId()).isEqualTo(UUID.fromString("0a35fb14-75ee-4125-943b-500893c30d32"));
        assertThat(deserialized.origin()).isEqualTo(OperationOrigin.USER);
    }

    @Test
    @DisplayName("Should serialize and deserialize Deposit command record with complete data")
    void shouldSerializeAndDeserializeDepositCommand() {
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("250.00");

        Deposit original = new Deposit(walletId, userId, amount, operationId, OperationOrigin.USER);

        byte[] bytes = objectMapper.writeValueAsBytes(original);
        Deposit deserialized = deserializePayload(bytes, Deposit.class);

        assertThat(deserialized).isNotNull();
        assertThat(deserialized.walletId()).isEqualTo(walletId);
        assertThat(deserialized.userId()).isEqualTo(userId);
        assertThat(deserialized.amount()).isEqualByComparingTo(amount);
        assertThat(deserialized.operationId()).isEqualTo(operationId);
        assertThat(deserialized.origin()).isEqualTo(OperationOrigin.USER);

        assertThat(deserialized.traceTags())
                .containsEntry("wallet.id", walletId.toString())
                .containsEntry("operation.origin", "USER");
    }

    @Test
    @DisplayName("Should serialize and deserialize Transfer command record with complete data")
    void shouldSerializeAndDeserializeTransferCommand() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("80.50");

        Transfer original = new Transfer(from, to, amount, operationId, OperationOrigin.USER);

        byte[] bytes = objectMapper.writeValueAsBytes(original);
        Transfer deserialized = deserializePayload(bytes, Transfer.class);

        assertThat(deserialized).isNotNull();
        assertThat(deserialized.from()).isEqualTo(from);
        assertThat(deserialized.to()).isEqualTo(to);
        assertThat(deserialized.amount()).isEqualByComparingTo(amount);
        assertThat(deserialized.operationId()).isEqualTo(operationId);
        assertThat(deserialized.origin()).isEqualTo(OperationOrigin.USER);

        assertThat(deserialized.traceTags())
                .containsEntry("wallet.from", from.toString())
                .containsEntry("wallet.to", to.toString())
                .containsEntry("operation.origin", "USER");
    }

    @Test
    @DisplayName("Should serialize and deserialize Wallet command record with complete data")
    void shouldSerializeAndDeserializeWalletCommand() {
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        BigDecimal initialBalance = new BigDecimal("1000.00");

        Wallet original = new Wallet(walletId, initialBalance, userId, operationId);

        byte[] bytes = objectMapper.writeValueAsBytes(original);
        Wallet deserialized = deserializePayload(bytes, Wallet.class);

        assertThat(deserialized).isNotNull();
        assertThat(deserialized.id()).isEqualTo(walletId);
        assertThat(deserialized.userId()).isEqualTo(userId);
        assertThat(deserialized.amount()).isEqualByComparingTo(initialBalance);
        assertThat(deserialized.operationId()).isEqualTo(operationId);

        assertThat(deserialized.traceTags())
                .containsEntry("user.id", userId.toString())
                .containsEntry("wallet.id", walletId.toString());
    }
}
