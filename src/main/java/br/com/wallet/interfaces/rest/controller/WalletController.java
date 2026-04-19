package br.com.wallet.interfaces.rest.controller;

import br.com.wallet.application.usecase.BalanceUseCase;
import br.com.wallet.application.usecase.CreateWalletUseCase;
import br.com.wallet.application.usecase.LedgerUseCase;
import br.com.wallet.application.usecase.ReplayWalletUseCase;
import br.com.wallet.domain.context.Wallet;
import br.com.wallet.interfaces.rest.api.WalletApi;
import br.com.wallet.interfaces.rest.dto.BalanceResponse;
import br.com.wallet.interfaces.rest.dto.CreateWalletCommand;
import br.com.wallet.interfaces.rest.dto.CreateWalletResponse;
import br.com.wallet.interfaces.rest.dto.LedgerEntryResponse;
import br.com.wallet.interfaces.rest.mapper.LedgerMapper;
import jakarta.validation.Valid;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/wallets")
public class WalletController implements WalletApi {

    private final BalanceUseCase balanceUseCase;
    private final CreateWalletUseCase createWalletUseCase;
    private final LedgerUseCase ledgerUseCase;
    private final ReplayWalletUseCase replayWalletUseCase;

    public WalletController(final BalanceUseCase balanceUseCase,
                            final CreateWalletUseCase createWalletUseCase, LedgerUseCase ledgerUseCase, ReplayWalletUseCase replayWalletUseCase) {
        this.balanceUseCase = balanceUseCase;
        this.createWalletUseCase = createWalletUseCase;
        this.ledgerUseCase = ledgerUseCase;
        this.replayWalletUseCase = replayWalletUseCase;
    }

    @GetMapping("/{walletId}/balance")
    @Override
    public BalanceResponse getBalance(@PathVariable final UUID walletId) {
        return new BalanceResponse(
                balanceUseCase.getBalance(walletId)
        );
    }

    @GetMapping("/{walletId}/balance/historical")
    @Override
    public BalanceResponse getHistorical(@PathVariable final UUID walletId, @RequestParam final Instant at
    ) {
        return new BalanceResponse(
                balanceUseCase.getHistoricalBalance(walletId, at)
        );
    }

    /**
     * Create Wallet operation
     * @param operationId Idempotency key: is only useful for money operations, if initial balance is null or zero it is ignored
     * @param command Create Wallet Command
     * @return Wallet Response (wallet ID)
     */
    @PostMapping
    @Override
    public ResponseEntity<CreateWalletResponse> createWallet(
            @RequestHeader(value = "Idempotency-Key", required = false) UUID operationId,
            @Valid @RequestBody(required = false) CreateWalletCommand command
    ) {
        final var initialBalance = resolveInitialBalance(command);

        final UUID walletId = UUID.randomUUID();
        if (initialBalance.compareTo(BigDecimal.ZERO) > 0 && operationId != null) {
            createWalletUseCase.handle(new Wallet(walletId, initialBalance, operationId));
        } else {
            createWalletUseCase.handle(walletId);
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateWalletResponse(walletId));
    }

    /**
     * As this is an O(n) endpoint as ledger grows, the response time, and resource consumption will rise, we have a limitation optional input
     *
     * @param walletId wallet id
     * @param limit    optional limit input ( default to 100 )
     * @return ledger entries
     */
    @GetMapping("/{walletId}/ledger")
    @Override
    public ResponseEntity<List<LedgerEntryResponse>> getLedger(
            @PathVariable UUID walletId,
            @RequestParam(defaultValue = "100") Integer limit
    ) {
        final var entries = ledgerUseCase.getLedger(walletId, limit)
                .stream()
                .map(LedgerMapper::toResponse)
                .toList();

        return ResponseEntity.of(Optional.of(entries));
    }

    @GetMapping("/{walletId}/replay")
    @Override
    public BalanceResponse replay(@PathVariable UUID walletId) {
        return new BalanceResponse(replayWalletUseCase.execute(walletId));
    }

    /**
     * Provide save initial balance from command inputs
     * @param command Create Wallet Command
     * @return initial balance
     */
    private @NonNull BigDecimal resolveInitialBalance(final CreateWalletCommand command) {
        return command != null && command.initialBalance() != null
                ? command.initialBalance()
                : BigDecimal.ZERO;
    }
}
