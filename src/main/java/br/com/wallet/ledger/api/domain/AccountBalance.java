package br.com.wallet.ledger.api.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountBalance(UUID userId, BigDecimal balance) {

}
