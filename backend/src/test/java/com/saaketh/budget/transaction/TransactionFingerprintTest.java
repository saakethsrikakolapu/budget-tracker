package com.saaketh.budget.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TransactionFingerprintTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 22);

    @Test
    void isA64CharacterHexHash() {
        assertThat(TransactionFingerprint.of(DAY, new BigDecimal("-4.75"), "CAMPUS COFFEE CO"))
                .hasSize(64)
                .matches("[0-9a-f]+");
    }

    @Test
    void ignoresCaseAndExtraWhitespaceInDescription() {
        String a = TransactionFingerprint.of(DAY, new BigDecimal("-4.75"), "Campus  Coffee\tCo");
        String b = TransactionFingerprint.of(DAY, new BigDecimal("-4.75"), "  CAMPUS COFFEE CO ");

        assertThat(a).isEqualTo(b);
    }

    @Test
    void treatsAmountScaleAsTheSameValue() {
        assertThat(TransactionFingerprint.of(DAY, new BigDecimal("-4.7"), "X"))
                .isEqualTo(TransactionFingerprint.of(DAY, new BigDecimal("-4.70"), "X"));
    }

    @Test
    void differsWhenDateAmountOrDescriptionDiffers() {
        String base = TransactionFingerprint.of(DAY, new BigDecimal("-4.75"), "COFFEE");

        assertThat(TransactionFingerprint.of(DAY.plusDays(1), new BigDecimal("-4.75"), "COFFEE")).isNotEqualTo(base);
        assertThat(TransactionFingerprint.of(DAY, new BigDecimal("4.75"), "COFFEE")).isNotEqualTo(base);
        assertThat(TransactionFingerprint.of(DAY, new BigDecimal("-4.75"), "COFFEE SHOP")).isNotEqualTo(base);
    }
}
