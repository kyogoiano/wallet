package br.com.wallet.integration.security;

import br.com.wallet.ledger.api.domain.LedgerType;
import br.com.wallet.ledger.internal.operation.Operation;
import br.com.wallet.ledger.internal.persistence.AccountDao;
import br.com.wallet.ledger.internal.persistence.LedgerDao;
import br.com.wallet.ledger.internal.persistence.WalletOperationsDao;
import br.com.wallet.ledger.internal.service.WalletOperationService;
import br.com.wallet.support.DatabaseCleaner;
import br.com.wallet.support.DockerProperties;
import br.com.wallet.support.IntegrationTestBase;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.PublishOptions;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@DisplayName("NatsPublisherAuthorizationIT: Core NATS Publisher Authorization & Transport Boundary (I-SEC-009, REQ-SEC-017, TASK-SEC-5.5)")
class NatsPublisherAuthorizationIT extends DockerProperties {

    @Autowired
    private Connection natsConnection;

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private LedgerDao ledgerDao;

    @Autowired
    private WalletOperationsDao walletOperationsDao;

    @Autowired
    private WalletOperationService walletOperationService;

    @Autowired
    private DatabaseCleaner cleaner;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        cleaner.clean();
    }

    private void seedAccount(UUID walletId, UUID userId, BigDecimal initialBalance, String tenantId) {
        accountDao.insertAccount(walletId, userId, tenantId);
        if (initialBalance != null && initialBalance.compareTo(BigDecimal.ZERO) > 0) {
            walletOperationService.applyTransaction(
                    walletId,
                    initialBalance,
                    LedgerType.CREDIT,
                    UUID.randomUUID(),
                    userId,
                    Instant.now(),
                    tenantId
            );
        }
    }

    private void publishCommand(String subject, Headers headers, String jsonPayload) throws Exception {
        JetStream jetStream = natsConnection.jetStream();
        NatsMessage message = NatsMessage.builder()
                .subject(subject)
                .headers(headers)
                .data(jsonPayload.getBytes(StandardCharsets.UTF_8))
                .build();
        PublishOptions options = PublishOptions.builder()
                .expectedStream("commands")
                .build();
        jetStream.publish(message, options);
    }

    private Optional<Operation> awaitOperation(UUID opId, Duration timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            Optional<Operation> op = walletOperationsDao.findOperation(opId);
            if (op.isPresent()) {
                String status = op.get().status().name();
                if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
                    return op;
                }
            }
            Thread.sleep(100);
        }
        return walletOperationsDao.findOperation(opId);
    }

    @Test
    @DisplayName("Positive: command published with authorized Edge credentials (edge-gateway) is accepted and processed")
    void shouldAcceptAndProcessCommandFromAuthorizedEdgePublisher() throws Exception {
        UUID fromWallet = UUID.randomUUID();
        UUID toWallet = UUID.randomUUID();
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        UUID opId = UUID.randomUUID();
        String tenantId = "tenant-enterprise-alpha";

        seedAccount(fromWallet, user1, new BigDecimal("500.00"), tenantId);
        seedAccount(toWallet, user2, new BigDecimal("100.00"), tenantId);

        Headers headers = new Headers();
        headers.add("Nats-Msg-Id", opId.toString());
        headers.add("operation_id", opId.toString());
        headers.add("type", "TRANSFER");
        headers.add("tenant_id", tenantId);
        headers.add("principal_id", "principal-edge-1");
        headers.add("key_id", "key-edge-1");
        headers.add("publisher_id", "edge-gateway");

        String payload = """
                {
                    "operationId": "%s",
                    "from": "%s",
                    "to": "%s",
                    "amount": 50.00
                }
                """.formatted(opId, fromWallet, toWallet);

        publishCommand("commands.wallet.transfer", headers, payload);

        Optional<Operation> opOpt = awaitOperation(opId, Duration.ofSeconds(10));
        assertThat(opOpt).isPresent();
        Operation op = opOpt.get();
        assertThat(op.status().name()).isEqualTo("COMPLETED");
        assertThat(op.tenantId()).isEqualTo(tenantId);

        BigDecimal fromBal = accountDao.findWalletBalance(fromWallet).orElseThrow();
        BigDecimal toBal = accountDao.findWalletBalance(toWallet).orElseThrow();
        assertThat(fromBal.setScale(2, RoundingMode.UNNECESSARY)).isEqualTo(new BigDecimal("450.00"));
        assertThat(toBal.setScale(2, RoundingMode.UNNECESSARY)).isEqualTo(new BigDecimal("150.00"));

        Integer ledgerCount = jdbc.queryForObject(
                "SELECT count(*) FROM ledger WHERE operation_id = ?",
                Integer.class,
                opId
        );
        assertThat(ledgerCount).isEqualTo(2);
    }

    @Test
    @DisplayName("Security: command with forged tenant in payload is overridden by authenticated transport header")
    void shouldOverrideForgedPayloadTenantWithTransportTenant() throws Exception {
        UUID fromWallet = UUID.randomUUID();
        UUID toWallet = UUID.randomUUID();
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        UUID opId = UUID.randomUUID();
        String transportTenant = "tenant-legit-corp";

        seedAccount(fromWallet, user1, new BigDecimal("300.00"), transportTenant);
        seedAccount(toWallet, user2, new BigDecimal("50.00"), transportTenant);

        Headers headers = new Headers();
        headers.add("Nats-Msg-Id", opId.toString());
        headers.add("operation_id", opId.toString());
        headers.add("type", "TRANSFER");
        headers.add("tenant_id", transportTenant);
        headers.add("principal_id", "principal-edge-1");
        headers.add("key_id", "key-edge-1");
        headers.add("publisher_id", "edge-gateway");

        // Attacker injected a forged tenantId inside the JSON payload body
        String forgedPayload = """
                {
                    "operationId": "%s",
                    "from": "%s",
                    "to": "%s",
                    "amount": 40.00,
                    "tenantId": "attacker-spoofed-tenant-beta"
                }
                """.formatted(opId, fromWallet, toWallet);

        publishCommand("commands.wallet.transfer", headers, forgedPayload);

        Optional<Operation> opOpt = awaitOperation(opId, Duration.ofSeconds(10));
        assertThat(opOpt).isPresent();
        Operation op = opOpt.get();
        assertThat(op.status().name()).isEqualTo("COMPLETED");
        // Must reflect transport header tenant, not forged payload tenant
        assertThat(op.tenantId()).isEqualTo(transportTenant);

        BigDecimal fromBal = accountDao.findWalletBalance(fromWallet).orElseThrow();
        assertThat(fromBal.setScale(2, RoundingMode.UNNECESSARY)).isEqualTo(new BigDecimal("260.00"));
    }

    @Test
    @DisplayName("Boundary: command from unauthorized publisher identity is rejected with FORBIDDEN_TENANT_ACCESS")
    void shouldRejectCommandFromUnauthorizedPublisherIdentity() throws Exception {
        UUID fromWallet = UUID.randomUUID();
        UUID toWallet = UUID.randomUUID();
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        UUID opId = UUID.randomUUID();
        String tenantId = "tenant-gamma";

        seedAccount(fromWallet, user1, new BigDecimal("500.00"), tenantId);
        seedAccount(toWallet, user2, BigDecimal.ZERO, tenantId);

        Headers headers = new Headers();
        headers.add("Nats-Msg-Id", opId.toString());
        headers.add("operation_id", opId.toString());
        headers.add("type", "TRANSFER");
        headers.add("tenant_id", tenantId);
        headers.add("principal_id", "principal-rogue");
        headers.add("key_id", "key-rogue");
        headers.add("publisher_id", "unauthorized-external-actor"); // Unauthorized identity

        String payload = """
                {
                    "operationId": "%s",
                    "from": "%s",
                    "to": "%s",
                    "amount": 100.00
                }
                """.formatted(opId, fromWallet, toWallet);

        publishCommand("commands.wallet.transfer", headers, payload);

        Optional<Operation> opOpt = awaitOperation(opId, Duration.ofSeconds(10));
        assertThat(opOpt).isPresent();
        Operation op = opOpt.get();
        assertThat(op.status().name()).isEqualTo("FAILED");
        assertThat(op.failureType()).isEqualTo("FORBIDDEN_TENANT_ACCESS");
        assertThat(op.errorMessage()).contains("Unauthorized publisher identity");

        // Balances must remain strictly unmodified
        BigDecimal fromBal = accountDao.findWalletBalance(fromWallet).orElseThrow();
        assertThat(fromBal.setScale(2, RoundingMode.UNNECESSARY)).isEqualTo(new BigDecimal("500.00"));

        // Zero ledger entries
        Integer ledgerCount = jdbc.queryForObject(
                "SELECT count(*) FROM ledger WHERE operation_id = ?",
                Integer.class,
                opId
        );
        assertThat(ledgerCount).isEqualTo(0);
    }

    @Test
    @DisplayName("Boundary: command missing publisher_id header is rejected with FORBIDDEN_TENANT_ACCESS")
    void shouldRejectCommandMissingPublisherIdHeader() throws Exception {
        UUID fromWallet = UUID.randomUUID();
        UUID toWallet = UUID.randomUUID();
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        UUID opId = UUID.randomUUID();
        String tenantId = "tenant-delta";

        seedAccount(fromWallet, user1, new BigDecimal("200.00"), tenantId);
        seedAccount(toWallet, user2, BigDecimal.ZERO, tenantId);

        Headers headers = new Headers();
        headers.add("Nats-Msg-Id", opId.toString());
        headers.add("operation_id", opId.toString());
        headers.add("type", "TRANSFER");
        headers.add("tenant_id", tenantId);
        headers.add("principal_id", "principal-1");
        headers.add("key_id", "key-1");
        // missing publisher_id header completely

        String payload = """
                {
                    "operationId": "%s",
                    "from": "%s",
                    "to": "%s",
                    "amount": 50.00
                }
                """.formatted(opId, fromWallet, toWallet);

        publishCommand("commands.wallet.transfer", headers, payload);

        Optional<Operation> opOpt = awaitOperation(opId, Duration.ofSeconds(10));
        assertThat(opOpt).isPresent();
        Operation op = opOpt.get();
        assertThat(op.status().name()).isEqualTo("FAILED");
        assertThat(op.failureType()).isEqualTo("FORBIDDEN_TENANT_ACCESS");

        BigDecimal fromBal = accountDao.findWalletBalance(fromWallet).orElseThrow();
        assertThat(fromBal.setScale(2, RoundingMode.UNNECESSARY)).isEqualTo(new BigDecimal("200.00"));

        Integer ledgerCount = jdbc.queryForObject(
                "SELECT count(*) FROM ledger WHERE operation_id = ?",
                Integer.class,
                opId
        );
        assertThat(ledgerCount).isEqualTo(0);
    }

    @Test
    @DisplayName("Boundary: command missing tenant_id header is rejected with FORBIDDEN_TENANT_ACCESS")
    void shouldRejectCommandMissingTenantIdHeader() throws Exception {
        UUID fromWallet = UUID.randomUUID();
        UUID toWallet = UUID.randomUUID();
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        UUID opId = UUID.randomUUID();

        seedAccount(fromWallet, user1, new BigDecimal("200.00"), "default");
        seedAccount(toWallet, user2, BigDecimal.ZERO, "default");

        Headers headers = new Headers();
        headers.add("Nats-Msg-Id", opId.toString());
        headers.add("operation_id", opId.toString());
        headers.add("type", "TRANSFER");
        headers.add("principal_id", "principal-1");
        headers.add("key_id", "key-1");
        headers.add("publisher_id", "edge-gateway");
        // missing tenant_id header completely

        String payload = """
                {
                    "operationId": "%s",
                    "from": "%s",
                    "to": "%s",
                    "amount": 50.00
                }
                """.formatted(opId, fromWallet, toWallet);

        publishCommand("commands.wallet.transfer", headers, payload);

        Optional<Operation> opOpt = awaitOperation(opId, Duration.ofSeconds(10));
        assertThat(opOpt).isPresent();
        Operation op = opOpt.get();
        assertThat(op.status().name()).isEqualTo("FAILED");
        assertThat(op.failureType()).isEqualTo("FORBIDDEN_TENANT_ACCESS");
        assertThat(op.errorMessage()).contains("Missing mandatory tenant_id header");

        BigDecimal fromBal = accountDao.findWalletBalance(fromWallet).orElseThrow();
        assertThat(fromBal.setScale(2, RoundingMode.UNNECESSARY)).isEqualTo(new BigDecimal("200.00"));

        Integer ledgerCount = jdbc.queryForObject(
                "SELECT count(*) FROM ledger WHERE operation_id = ?",
                Integer.class,
                opId
        );
        assertThat(ledgerCount).isEqualTo(0);
    }
}
