package br.com.wallet.interfaces.rest.controller;

import br.com.wallet.application.fraud.FraudCheckHelper;
import br.com.wallet.application.usecase.*;
import br.com.wallet.domain.Account;
import br.com.wallet.domain.context.Wallet;
import br.com.wallet.infrasctructure.messaging.publisher.NatsCommandPublisher;
import br.com.wallet.interfaces.rest.api.WalletApi;
import br.com.wallet.interfaces.rest.dto.*;
import br.com.wallet.interfaces.rest.mapper.LedgerMapper;
import jakarta.validation.Valid;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/wallets")
public class WalletController implements WalletApi {

    private static final Logger log = LoggerFactory.getLogger(WalletController.class);
    private final NatsCommandPublisher natsCommandPublisher;
    private final BalanceUseCase balanceUseCase;
    private final CreateWalletUseCase createWalletUseCase;
    private final LedgerUseCase ledgerUseCase;
    private final ReplayWalletUseCase replayWalletUseCase;
    private final AccountUseCase accountUseCase;
    private final FraudCheckHelper fraudCheckHelper;

    public WalletController(final NatsCommandPublisher natsCommandPublisher,
                            final BalanceUseCase balanceUseCase,
                            final CreateWalletUseCase createWalletUseCase,
                            final LedgerUseCase ledgerUseCase,
                            final ReplayWalletUseCase replayWalletUseCase,
                            final AccountUseCase accountUseCase,
                            final FraudCheckHelper fraudCheckHelper) {
        this.natsCommandPublisher = natsCommandPublisher;
        this.balanceUseCase = balanceUseCase;
        this.createWalletUseCase = createWalletUseCase;
        this.ledgerUseCase = ledgerUseCase;
        this.replayWalletUseCase = replayWalletUseCase;
        this.accountUseCase = accountUseCase;
        this.fraudCheckHelper = fraudCheckHelper;
    }

    @GetMapping("/{walletId}")
    @Override
    public AccountResponse getAccount(@PathVariable final UUID walletId) {
        final var account = accountUseCase.find(walletId);
        return new AccountResponse(
                account.id(), account.balance(), account.version(), account.userId(), account.createdAt());
    }

    @GetMapping()
    @Override
    public List<AccountResponse> list(@RequestParam(defaultValue = "100") Integer limit, @RequestParam(defaultValue = "0") Integer offset) {
        return mapToAccountResponse(accountUseCase.list(limit, offset));
    }

    private @NonNull List<@NonNull AccountResponse> mapToAccountResponse(@NonNull final List<@NonNull Account> list) {
        return list.stream()
                .map(account -> new AccountResponse(
                        account.id(), account.balance(), account.version(), account.userId(), account.createdAt() ) ).toList();
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
    public BalanceResponse getHistorical(@PathVariable final UUID walletId, @RequestParam final Instant at) {
        return new BalanceResponse(
                balanceUseCase.getHistoricalBalance(walletId, at)
        );
    }

    /**
     * Simple wallet creation (Synchronous)
     */
    @PostMapping("/{userId}")
    @Override
    public ResponseEntity<CreateWalletResponse> create(@PathVariable final UUID userId) {
        final UUID walletId = UUID.randomUUID();
        log.info("Sync wallet creation requested (zero balance). walletId={}, userId={}", walletId, userId);
        createWalletUseCase.handle(walletId, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateWalletResponse(walletId));
    }

    /**
     * Wallet creation with initial deposit (Asynchronous)
     */
    @PostMapping("/deposit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Override
    public CompletableFuture<ResponseEntity<CreateWalletResponse>> createWithDeposit(
            @RequestHeader(value = "Idempotency-Key") UUID operationId,
            @Valid @RequestBody CreateWalletCommand command
    ) {
        final var initialBalance = command.initialBalance() != null ? command.initialBalance() : BigDecimal.ZERO;
        final UUID walletId = UUID.randomUUID();

        if (initialBalance.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Initial balance must be greater than zero for this endpoint");
        }

        log.info("Async wallet creation with deposit requested. walletId={}, userId={}, amount={}", walletId, command.userId()
                , initialBalance);
        final var wallet = new Wallet(walletId, initialBalance, command.userId(), operationId);

        fraudCheckHelper.performFraudCheck(wallet);

        return natsCommandPublisher.publishAsync("commands.wallet", wallet)
                .thenApply(ack -> {
                    log.debug("Create wallet command ACKed by NATS: seq={}", ack.getSeqno());
                    return ResponseEntity.status(HttpStatus.ACCEPTED)
                            .body(new CreateWalletResponse(walletId));
                });
    }

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
}
