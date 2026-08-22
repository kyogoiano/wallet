package br.com.wallet.wallet.api.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountBalance(UUID userId, BigDecimal balance) {

}
