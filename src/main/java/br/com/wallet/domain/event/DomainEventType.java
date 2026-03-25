package br.com.wallet.domain.event;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum DomainEventType {
    TRANSFER_COMPLETED(TransferCompletedEvent.class),
    DEPOSIT_COMPLETED(DepositCompletedEvent.class),
    WITHDRAW_COMPLETED(WithdrawCompletedEvent.class);

    private static final Map<String, DomainEventType> MAP =
            Arrays.stream(values())
                    .collect(Collectors.toMap(
                            e -> e.name().toUpperCase(),
                            Function.identity()
                    ));

    final Class<? extends DomainEvent> clazz;

    DomainEventType(Class<? extends DomainEvent> clazz) {
        this.clazz = clazz;
    }

    public Class<? extends DomainEvent> getClazz() {
        return clazz;
    }




    @Nullable
    public static DomainEventType fromString(@NonNull final String value) {
        return MAP.get(value.toUpperCase());
    }
}
