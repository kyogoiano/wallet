package br.com.wallet.interfaces.rest.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(UUID id, BigDecimal balance, Long version, UUID userId, Instant createdAt) {

}
