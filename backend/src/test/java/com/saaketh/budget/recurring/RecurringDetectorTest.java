package com.saaketh.budget.recurring;

import static org.assertj.core.api.Assertions.assertThat;

import com.saaketh.budget.recurring.RecurringDetector.Cadence;
import com.saaketh.budget.recurring.RecurringDetector.Charge;
import com.saaketh.budget.recurring.RecurringDetector.Detection;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecurringDetectorTest {

    private static final LocalDate JAN_15 = LocalDate.of(2026, 1, 15);

    @Test
    void merchantKeyDropsBankNoise() {
        assertThat(MerchantKey.of("PURCHASE AUTHORIZED ON 10/27 NETFLIX.COM CARD 1234")).isEqualTo("NETFLIX COM");
        assertThat(MerchantKey.of("Netflix.com")).isEqualTo("NETFLIX COM");
        assertThat(MerchantKey.of("SPOTIFY USA P2C1A9F3E1")).isEqualTo("SPOTIFY");
        assertThat(MerchantKey.of("PLANET FITNESS #0423")).isEqualTo("PLANET FITNESS");
        assertThat(MerchantKey.of("SQ *JOES COFFEE 4471")).isEqualTo("JOES COFFEE");
        assertThat(MerchantKey.of("VERIZON WIRELESS PAYMENTS")).isEqualTo("VERIZON WIRELESS");
        assertThat(MerchantKey.of("12345")).isEqualTo("12345"); // nothing left: keep the original
    }

    @Test
    void monthlySubscriptionWithJitterAndPriceChange() {
        List<Charge> charges = List.of(
                charge("2026-01-15", "NETFLIX.COM", "-15.49"),
                charge("2026-02-14", "NETFLIX.COM", "-15.49"),
                charge("2026-03-17", "NETFLIX.COM", "-15.49"),
                charge("2026-04-15", "NETFLIX.COM", "-17.99"), // price went up
                charge("2026-05-16", "NETFLIX.COM", "-17.99"));

        Detection d = only(RecurringDetector.detect(charges, LocalDate.parse("2026-05-31")));

        assertThat(d.merchantKey()).isEqualTo("NETFLIX COM");
        assertThat(d.cadence()).isEqualTo(Cadence.MONTHLY);
        assertThat(d.amount()).isEqualByComparingTo("17.99"); // the current price
        assertThat(d.monthlyCost()).isEqualByComparingTo("17.99");
        assertThat(d.chargeCount()).isEqualTo(5);
        assertThat(d.nextExpected()).isEqualTo(LocalDate.parse("2026-06-16"));
        assertThat(d.active()).isTrue();
    }

    @Test
    void oneSkippedMonthStillCounts() {
        List<Charge> charges = List.of(
                charge("2026-01-05", "GYM", "-40.00"),
                charge("2026-03-05", "GYM", "-40.00"), // February missing
                charge("2026-04-05", "GYM", "-40.00"));

        assertThat(only(RecurringDetector.detect(charges, LocalDate.parse("2026-04-20"))).cadence())
                .isEqualTo(Cadence.MONTHLY);
    }

    @Test
    void stoppedChargesLookCancelled() {
        List<Charge> charges = monthly("HULU", "-7.99", JAN_15, 4); // Jan..Apr

        assertThat(only(RecurringDetector.detect(charges, LocalDate.parse("2026-05-20"))).active()).isTrue();
        assertThat(only(RecurringDetector.detect(charges, LocalDate.parse("2026-07-01"))).active()).isFalse();
    }

    @Test
    void cadencesAndMonthlyCost() {
        List<Charge> charges = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            charges.add(charge(JAN_15.plusWeeks(i).toString(), "LAUNDRY CARD", "-10.00"));
            charges.add(charge(JAN_15.plusWeeks(2L * i).toString(), "TUTOR", "-50.00"));
        }
        for (int i = 0; i < 3; i++) {
            charges.add(charge(JAN_15.plusMonths(3L * i).toString(), "STATE FARM", "-180.00"));
        }
        charges.add(charge("2025-03-01", "AMAZON PRIME*AB12", "-139.00"));
        charges.add(charge("2026-03-02", "AMAZON PRIME*ZX98", "-139.00"));

        List<Detection> found = RecurringDetector.detect(charges, LocalDate.parse("2026-04-01"));

        assertThat(found).extracting(Detection::merchantKey, Detection::cadence, Detection::monthlyCost)
                .containsExactly( // most expensive per month first
                        org.assertj.core.groups.Tuple.tuple("TUTOR", Cadence.BIWEEKLY, new BigDecimal("108.70")),
                        org.assertj.core.groups.Tuple.tuple("STATE FARM", Cadence.QUARTERLY, new BigDecimal("60.00")),
                        org.assertj.core.groups.Tuple.tuple("LAUNDRY", Cadence.WEEKLY, new BigDecimal("43.48")),
                        org.assertj.core.groups.Tuple.tuple("AMAZON PRIME", Cadence.YEARLY, new BigDecimal("11.58")));
    }

    @Test
    void variableBillsCountWhenTimingIsSpotless() {
        String[] amounts = {"-48.10", "-61.75", "-82.30", "-95.40", "-70.05", "-52.90"};
        List<Charge> charges = new ArrayList<>();
        for (int i = 0; i < amounts.length; i++) {
            charges.add(charge(JAN_15.plusMonths(i).toString(), "DUKE ENERGY", amounts[i]));
        }
        assertThat(only(RecurringDetector.detect(charges, LocalDate.parse("2026-07-01"))).cadence())
                .isEqualTo(Cadence.MONTHLY);
    }

    @Test
    void ignoresIrregularShoppingRefundsAndTooFewCharges() {
        List<Charge> charges = List.of(
                // groceries every week but the amounts are all over the place
                charge("2026-01-03", "HARRIS TEETER 123", "-34.10"),
                charge("2026-01-10", "HARRIS TEETER 123", "-112.80"),
                charge("2026-01-17", "HARRIS TEETER 123", "-58.00"),
                charge("2026-01-24", "HARRIS TEETER 123", "-19.45"),
                charge("2026-01-31", "HARRIS TEETER 123", "-87.20"),
                // coffee at the same price, but at random times
                charge("2026-01-02", "STARBUCKS", "-5.25"),
                charge("2026-01-03", "STARBUCKS", "-5.25"),
                charge("2026-01-12", "STARBUCKS", "-5.25"),
                charge("2026-01-29", "STARBUCKS", "-5.25"),
                charge("2026-01-30", "STARBUCKS", "-5.25"),
                // a paycheck (money in) every two weeks is not a charge
                charge("2026-01-09", "PAYROLL", "800.00"),
                charge("2026-01-23", "PAYROLL", "800.00"),
                charge("2026-02-06", "PAYROLL", "800.00"),
                // only two monthly charges: not enough evidence yet
                charge("2026-01-01", "NEW APP", "-4.99"),
                charge("2026-02-01", "NEW APP", "-4.99"));

        assertThat(RecurringDetector.detect(charges, LocalDate.parse("2026-02-10"))).isEmpty();
    }

    // --- helpers ---

    private static Detection only(List<Detection> detections) {
        assertThat(detections).hasSize(1);
        return detections.getFirst();
    }

    private static List<Charge> monthly(String description, String amount, LocalDate start, int count) {
        List<Charge> charges = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            charges.add(charge(start.plusMonths(i).toString(), description, amount));
        }
        return charges;
    }

    private static Charge charge(String date, String description, String amount) {
        return new Charge(LocalDate.parse(date), description, new BigDecimal(amount));
    }
}
