package br.com.wallet.ledger.api.event;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Domain event types.
 * They're strongly connected to database schema, so whenever changes you made here, might be changed on script constraint also!
 */
public enum DomainEventType {
    TRANSFER_COMPLETED(TransferCompletedEvent.class, "events.transfer.completed"),
    DEPOSIT_COMPLETED(DepositCompletedEvent.class, "events.deposit.completed"),
    WITHDRAW_COMPLETED(WithdrawCompletedEvent.class, "events.withdraw.completed"),
    FRAUD(FraudEvent.class, "events.fraud"),
    RISK_PROPAGATION_DETECTED(EntityRiskPropagationAlertEvent.class, "events.fraud.propagation");

    private static final Map<String, DomainEventType> MAP =
            Arrays.stream(values())
                    .collect(Collectors.toMap(
                            e -> e.name().toUpperCase(),
                            Function.identity()
                    ));

    final Class<? extends DomainEvent> clazz;
    final String subject;

    DomainEventType(Class<? extends DomainEvent> clazz, String subject) {
        this.clazz = clazz;
        this.subject = subject;
    }

    public Class<? extends DomainEvent> getClazz() {
        return clazz;
    }

    public String getSubject() {
        return subject;
    }

    @Nullable
    public static DomainEventType fromString(@NonNull final String value) {
        return MAP.get(value.toUpperCase());
    }
}
