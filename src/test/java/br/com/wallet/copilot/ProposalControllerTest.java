package br.com.wallet.copilot;

import br.com.wallet.copilot.api.ProposalUseCase;
import br.com.wallet.copilot.api.dto.ApproveProposalCommand;
import br.com.wallet.copilot.api.dto.CreateProposalCommand;
import br.com.wallet.copilot.api.dto.ProposalResponse;
import br.com.wallet.copilot.api.dto.RejectProposalCommand;
import br.com.wallet.copilot.api.exception.IdempotencyConflictException;
import br.com.wallet.copilot.api.exception.ProposalConflictException;
import br.com.wallet.copilot.api.exception.ProposalExpiredException;
import br.com.wallet.copilot.api.exception.ProposalNotFoundException;
import br.com.wallet.copilot.api.model.ProposalStatus;
import br.com.wallet.copilot.api.model.ProposalType;
import br.com.wallet.copilot.internal.rest.ProposalController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProposalController REST Ingress Tests (TASK-4.10, REQ-COPILOT-007, I-AI-004)")
class ProposalControllerTest {

    @Mock
    private ProposalUseCase proposalUseCase;

    private MockMvc mockMvc;

    private final String tenantId = "tenant-rest";
    private final UUID walletId = UUID.randomUUID();
    private final UUID proposalId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ProposalController controller = new ProposalController(proposalUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private ProposalResponse buildResponse(UUID id, ProposalStatus status) {
        return new ProposalResponse(
                id,
                tenantId,
                walletId,
                ProposalType.TRANSFER,
                status,
                "{\"amount\":100}",
                "key-1",
                UUID.randomUUID().toString(),
                "agent-user",
                "human-mgr",
                Instant.now(),
                Instant.now().plusSeconds(900),
                null,
                null,
                null,
                null,
                null
        );
    }

    @Test
    @DisplayName("POST /proposals: Should create proposal and return 200 OK")
    void shouldCreateProposal() throws Exception {
        ProposalResponse response = buildResponse(proposalId, ProposalStatus.PROPOSED);
        when(proposalUseCase.createProposal(eq(tenantId), any(), any(CreateProposalCommand.class)))
                .thenReturn(response);

        String body = String.format("""
                {
                    "walletId": "%s",
                    "type": "TRANSFER",
                    "parametersJson": "{\\"amount\\":100}",
                    "idempotencyKey": "key-1"
                }
                """, walletId);

        mockMvc.perform(post("/api/v1/copilot/proposals")
                        .header("X-Tenant-Id", tenantId)
                        .header("X-User-Id", "agent-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(proposalId.toString()))
                .andExpect(jsonPath("$.status").value("PROPOSED"));
    }

    @Test
    @DisplayName("POST /proposals: Replay with conflicting payload returns 409 Conflict")
    void shouldReturn409OnIdempotencyConflict() throws Exception {
        when(proposalUseCase.createProposal(eq(tenantId), any(), any(CreateProposalCommand.class)))
                .thenThrow(new IdempotencyConflictException("Conflicting parameters"));

        String body = String.format("""
                {
                    "walletId": "%s",
                    "type": "TRANSFER",
                    "parametersJson": "{\\"amount\\":999}",
                    "idempotencyKey": "key-1"
                }
                """, walletId);

        mockMvc.perform(post("/api/v1/copilot/proposals")
                        .header("X-Tenant-Id", tenantId)
                        .header("X-User-Id", "agent-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /proposals/{id}/approve: Approving valid proposal returns 200 OK with EXECUTED")
    void shouldApproveProposal() throws Exception {
        ProposalResponse response = buildResponse(proposalId, ProposalStatus.EXECUTED);
        when(proposalUseCase.approveProposal(eq(tenantId), any(), any(ApproveProposalCommand.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/copilot/proposals/{id}/approve", proposalId)
                        .header("X-Tenant-Id", tenantId)
                        .header("X-User-Id", "human-mgr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXECUTED"));
    }

    @Test
    @DisplayName("I-AI-004: Approving unknown proposal or cross-tenant returns 404 Not Found")
    void shouldReturn404WhenNotFoundOrWrongTenant() throws Exception {
        when(proposalUseCase.approveProposal(eq(tenantId), any(), any(ApproveProposalCommand.class)))
                .thenThrow(new ProposalNotFoundException("Not found"));

        mockMvc.perform(post("/api/v1/copilot/proposals/{id}/approve", proposalId)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /proposals/{id}/approve: Expired proposal returns 409 Conflict")
    void shouldReturn409OnExpired() throws Exception {
        when(proposalUseCase.approveProposal(eq(tenantId), any(), any(ApproveProposalCommand.class)))
                .thenThrow(new ProposalExpiredException("Expired"));

        mockMvc.perform(post("/api/v1/copilot/proposals/{id}/approve", proposalId)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /proposals/{id}/reject: Rejection returns 200 OK with REJECTED")
    void shouldRejectProposal() throws Exception {
        ProposalResponse response = buildResponse(proposalId, ProposalStatus.REJECTED);
        when(proposalUseCase.rejectProposal(eq(tenantId), any(), any(RejectProposalCommand.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/copilot/proposals/{id}/reject", proposalId)
                        .header("X-Tenant-Id", tenantId)
                        .header("X-User-Id", "human-mgr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Manual rejection\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    @DisplayName("I-AI-004: GET /proposals/{id} returns 404 when proposal belongs to different tenant")
    void shouldReturn404OnCrossTenantGet() throws Exception {
        when(proposalUseCase.findById(proposalId, tenantId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/copilot/proposals/{id}", proposalId)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /proposals?walletId={w}: Lists pending proposals")
    void shouldListPendingProposals() throws Exception {
        ProposalResponse p1 = buildResponse(proposalId, ProposalStatus.PROPOSED);
        when(proposalUseCase.listPending(tenantId, walletId)).thenReturn(List.of(p1));

        mockMvc.perform(get("/api/v1/copilot/proposals")
                        .header("X-Tenant-Id", tenantId)
                        .param("walletId", walletId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(proposalId.toString()));
    }

    @Test
    @DisplayName("Should return 400 Bad Request when tenant header is missing")
    void shouldReturn400WhenTenantMissing() throws Exception {
        mockMvc.perform(get("/api/v1/copilot/proposals/{id}", proposalId))
                .andExpect(status().isBadRequest());
    }
}
