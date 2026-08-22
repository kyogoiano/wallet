package br.com.wallet.wallet.api.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Account(UUID id, BigDecimal balance, Long version, UUID userId, Instant createdAt) {

}
