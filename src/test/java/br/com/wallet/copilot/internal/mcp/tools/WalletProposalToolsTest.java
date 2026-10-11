package br.com.wallet.copilot.internal.mcp.tools;

import br.com.wallet.copilot.api.ProposalUseCase;
import br.com.wallet.copilot.api.dto.CreateProposalCommand;
import br.com.wallet.copilot.api.dto.ProposalResponse;
import br.com.wallet.copilot.api.exception.IdempotencyConflictException;
import br.com.wallet.copilot.api.model.ProposalStatus;
import br.com.wallet.copilot.api.model.ProposalType;
import br.com.wallet.copilot.internal.mcp.config.McpSecurityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletProposalToolsTest {

    @Mock
    private ProposalUseCase proposalUseCase;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WalletProposalTools tools;

    private final String tenantId = "tenant-alpha";
    private final String principal = "agent-copilot-1";
    private final UUID sourceWalletId = UUID.randomUUID();
    private final UUID targetWalletId = UUID.randomUUID();
    private final String validKey = "idemp-key-123";

    @BeforeEach
    void setUp() {
        McpSecurityContext.setContext(tenantId, principal);
        tools = new WalletProposalTools(proposalUseCase, objectMapper);
    }

    @AfterEach
    void tearDown() {
        McpSecurityContext.clear();
    }

    @Test
    @DisplayName("Positive: proposeTransfer creates proposal with valid scale-2 amount")
    void testProposeTransferPositive() {
        ProposalResponse expectedResponse = new ProposalResponse(
                UUID.randomUUID(), tenantId, sourceWalletId, ProposalType.TRANSFER,
                ProposalStatus.PROPOSED, "{}", validKey, UUID.randomUUID().toString(),
                principal, null, Instant.now(), Instant.now().plusSeconds(300),
                null, null, null, null, null
        );

        when(proposalUseCase.createProposal(eq(tenantId), eq(principal), any(CreateProposalCommand.class)))
                .thenReturn(expectedResponse);

        ProposalResponse response = tools.proposeTransfer(
                sourceWalletId.toString(), targetWalletId.toString(),
                "150.00", "Payment for services", validKey
        );

        assertThat(response.status()).isEqualTo(ProposalStatus.PROPOSED);
        assertThat(response.idempotencyKey()).isEqualTo(validKey);
    }

    @Test
    @DisplayName("Boundary: Non-canonical monetary strings rejected (scale != 2, negative, or > 10^8)")
    void testMonetaryPrecisionValidation() {
        // Missing decimals
        assertThatThrownBy(() -> tools.proposeTransfer(
                sourceWalletId.toString(), targetWalletId.toString(),
                "150", "Payment", validKey
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("canonical scale-2");

        // Scale 1
        assertThatThrownBy(() -> tools.proposeTransfer(
                sourceWalletId.toString(), targetWalletId.toString(),
                "150.5", "Payment", validKey
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("canonical scale-2");

        // Scale 3
        assertThatThrownBy(() -> tools.proposeTransfer(
                sourceWalletId.toString(), targetWalletId.toString(),
                "150.555", "Payment", validKey
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("canonical scale-2");

        // Negative
        assertThatThrownBy(() -> tools.proposeTransfer(
                sourceWalletId.toString(), targetWalletId.toString(),
                "-150.00", "Payment", validKey
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("canonical scale-2");

        // Zero
        assertThatThrownBy(() -> tools.proposeTransfer(
                sourceWalletId.toString(), targetWalletId.toString(),
                "0.00", "Payment", validKey
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("bounds");

        // Exceeds 100,000,000.00
        assertThatThrownBy(() -> tools.proposeTransfer(
                sourceWalletId.toString(), targetWalletId.toString(),
                "100000000.01", "Payment", validKey
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("bounds");
    }

    @Test
    @DisplayName("Boundary: Blank or missing idempotency key rejected")
    void testBlankIdempotencyKeyRejected() {
        assertThatThrownBy(() -> tools.proposeTransfer(
                sourceWalletId.toString(), targetWalletId.toString(),
                "150.00", "Payment", "   "
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("idempotencyKey");
    }

    @Test
    @DisplayName("Idempotency: Conflicting payload on same key throws IdempotencyConflictException")
    void testConflictingPayloadThrowsConflict() {
        when(proposalUseCase.createProposal(eq(tenantId), eq(principal), any(CreateProposalCommand.class)))
                .thenThrow(new IdempotencyConflictException("Payload mismatch on key"));

        assertThatThrownBy(() -> tools.proposeTransfer(
                sourceWalletId.toString(), targetWalletId.toString(),
                "150.00", "Payment", validKey
        )).isInstanceOf(IdempotencyConflictException.class);
    }
}
