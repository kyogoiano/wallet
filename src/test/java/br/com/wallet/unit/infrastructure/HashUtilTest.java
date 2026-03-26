package br.com.wallet.unit.infrastructure;

import br.com.wallet.domain.LedgerType;
import br.com.wallet.util.HashUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class HashUtilTest {

    @Test
    void shouldGenerateSameHashForEquivalentAmounts() {

        var walletId = UUID.randomUUID();
        var operationId = UUID.randomUUID();
        var sequence = 1L;
        var now = Instant.ofEpochMilli(1700000000000L);

        var amount1 = new BigDecimal("200");
        var amount2 = new BigDecimal("200.00");

        var input1 = HashUtils.buildLedgerHashInput(
                null,
                walletId,
                amount1,
                LedgerType.CREDIT,
                sequence,
                operationId,
                now
        );

        var input2 = HashUtils.buildLedgerHashInput(
                null,
                walletId,
                amount2,
                LedgerType.CREDIT,
                sequence,
                operationId,
                now
        );

        assertThat(input1).isEqualTo(input2);
    }

    @Test
    void shouldUseGenesisWhenPreviousHashIsNull() {

        var input = HashUtils.buildLedgerHashInput(
                null,
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                new BigDecimal("100.00"),
                LedgerType.CREDIT,
                1L,
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                Instant.ofEpochMilli(1700000000000L)
        );

        assertThat(input).startsWith("GENESIS|");
    }

    @Test
    void shouldBeDeterministicForSameInput() {

        var walletId = UUID.randomUUID();
        var operationId = UUID.randomUUID();
        var now = Instant.ofEpochMilli(1700000000000L);

        var input1 = HashUtils.buildLedgerHashInput(
                "prev",
                walletId,
                new BigDecimal("10.00"),
                LedgerType.DEBIT,
                2L,
                operationId,
                now
        );

        var input2 = HashUtils.buildLedgerHashInput(
                "prev",
                walletId,
                new BigDecimal("10.00"),
                LedgerType.DEBIT,
                2L,
                operationId,
                now
        );

        assertThat(input1).isEqualTo(input2);
    }
}
