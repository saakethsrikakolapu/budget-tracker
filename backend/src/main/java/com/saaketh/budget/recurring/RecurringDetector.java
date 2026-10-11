package com.saaketh.budget.recurring;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Finds recurring charges (subscriptions, bills) in a list of transactions. Pure logic: no
 * database, so it can be measured against labeled data.
 *
 * <p>A merchant's charges are recurring when:
 * <ol>
 *   <li>the gaps between them match a cadence (weekly ... yearly) within a tolerance, allowing
 *       one missed period;</li>
 *   <li>most amounts are within {@value #AMOUNT_TOLERANCE_PERCENT}% of the typical amount, so price
 *       changes still count but grocery runs don't (bills with spotless monthly timing get
 *       {@value #BILL_AMOUNT_TOLERANCE_PERCENT}%, since electric and phone bills vary);</li>
 *   <li>there are at least 3 charges (2 for yearly).</li>
 * </ol>
 */
public final class RecurringDetector {

    private RecurringDetector() {
    }

    /** One transaction as the detector sees it. amount: negative = spent. */
    public record Charge(LocalDate date, String description, BigDecimal amount) {
    }

    public enum Cadence {
        WEEKLY(7, 2, 3),
        BIWEEKLY(14, 3, 3),
        MONTHLY(30, 7, 3), // wide: months are 28-31 days and banks post late around weekends
        QUARTERLY(91, 10, 3),
        YEARLY(365, 15, 2);

        final int days;
        final int toleranceDays;
        final int minimumCharges;

        Cadence(int days, int toleranceDays, int minimumCharges) {
            this.days = days;
            this.toleranceDays = toleranceDays;
            this.minimumCharges = minimumCharges;
        }

        /** The date after `date` by one period (calendar-aware for months and years). */
        LocalDate next(LocalDate date) {
            return switch (this) {
                case MONTHLY -> date.plusMonths(1);
                case QUARTERLY -> date.plusMonths(3);
                case YEARLY -> date.plusYears(1);
                default -> date.plusDays(days);
            };
        }

        /** Multiply a per-charge amount by this to get a per-month amount. */
        BigDecimal perMonthFactor() {
            return switch (this) {
                case WEEKLY -> new BigDecimal("4.348"); // 52.18 weeks / 12
                case BIWEEKLY -> new BigDecimal("2.174");
                case MONTHLY -> BigDecimal.ONE;
                case QUARTERLY -> BigDecimal.ONE.divide(BigDecimal.valueOf(3), 6, RoundingMode.HALF_UP);
                case YEARLY -> BigDecimal.ONE.divide(BigDecimal.valueOf(12), 6, RoundingMode.HALF_UP);
            };
        }
    }

    /**
     * @param amount the most recent charge, as a positive number (the current price)
     * @param monthlyCost amount converted to a per-month figure
     * @param active false when the next charge is well overdue ("looks cancelled")
     */
    public record Detection(String merchantKey, String latestDescription, Cadence cadence, BigDecimal amount,
            BigDecimal monthlyCost, int chargeCount, LocalDate firstDate, LocalDate lastDate,
            LocalDate nextExpected, boolean active) {
    }

    static final int AMOUNT_TOLERANCE_PERCENT = 25;
    static final int BILL_AMOUNT_TOLERANCE_PERCENT = 50;
    /** Share of gaps / amounts that must fit (so one odd month doesn't hide a subscription). */
    static final double MOSTLY = 0.75;

    /**
     * @param asOf "today" for deciding active vs cancelled: the latest date the data covers, since
     *     statements are uploaded after the fact
     */
    public static List<Detection> detect(List<Charge> charges, LocalDate asOf) {
        Map<String, List<Charge>> byMerchant = charges.stream()
                .filter(c -> c.amount().signum() < 0) // spending only
                .collect(Collectors.groupingBy(c -> MerchantKey.of(c.description())));

        List<Detection> detections = new ArrayList<>();
        for (Map.Entry<String, List<Charge>> entry : byMerchant.entrySet()) {
            List<Charge> sorted = entry.getValue().stream().sorted(Comparator.comparing(Charge::date)).toList();
            analyze(entry.getKey(), sorted, asOf).ifPresent(detections::add);
        }
        detections.sort(Comparator.comparing(Detection::monthlyCost).reversed().thenComparing(Detection::merchantKey));
        return detections;
    }

    private static Optional<Detection> analyze(String key, List<Charge> charges, LocalDate asOf) {
        if (charges.size() < 2) {
            return Optional.empty();
        }
        List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < charges.size(); i++) {
            gaps.add(ChronoUnit.DAYS.between(charges.get(i - 1).date(), charges.get(i).date()));
        }
        long medianGap = median(gaps);

        for (Cadence cadence : Cadence.values()) {
            if (charges.size() < cadence.minimumCharges || Math.abs(medianGap - cadence.days) > cadence.toleranceDays) {
                continue;
            }
            long misses = misses(gaps, cadence);
            if (misses > 1 || misses > (1 - MOSTLY) * gaps.size()) {
                return Optional.empty();
            }
            // Bills that vary month to month (electric, phone) are allowed a wider range, but only
            // when the timing is spotless, so irregular shopping with steady prices can't sneak in.
            boolean billLike = cadence.days >= Cadence.MONTHLY.days && misses == 0 && charges.size() >= 4;
            if (!similarAmounts(charges, billLike ? BILL_AMOUNT_TOLERANCE_PERCENT : AMOUNT_TOLERANCE_PERCENT)) {
                return Optional.empty();
            }
            Charge last = charges.getLast();
            BigDecimal amount = last.amount().abs();
            LocalDate next = cadence.next(last.date());
            // Overdue by more than half a period (plus tolerance): probably cancelled.
            boolean active = !asOf.isAfter(next.plusDays(cadence.days / 2 + cadence.toleranceDays));
            return Optional.of(new Detection(key, last.description(), cadence, amount,
                    amount.multiply(cadence.perMonthFactor()).setScale(2, RoundingMode.HALF_UP),
                    charges.size(), charges.getFirst().date(), last.date(), next, active));
        }
        return Optional.empty();
    }

    /** Gaps that are neither one period nor two (a skipped charge counts as fitting). */
    private static long misses(List<Long> gaps, Cadence cadence) {
        return gaps.stream()
                .filter(g -> Math.abs(g - cadence.days) > cadence.toleranceDays
                        && Math.abs(g - 2L * cadence.days) > 2L * cadence.toleranceDays)
                .count();
    }

    /** Most charges within tolerancePercent of the median charge. */
    private static boolean similarAmounts(List<Charge> charges, int tolerancePercent) {
        List<BigDecimal> amounts = charges.stream().map(c -> c.amount().abs()).sorted().toList();
        BigDecimal median = amounts.get(amounts.size() / 2);
        BigDecimal tolerance = median.multiply(BigDecimal.valueOf(tolerancePercent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        long close = amounts.stream().filter(a -> a.subtract(median).abs().compareTo(tolerance) <= 0).count();
        return close >= MOSTLY * amounts.size();
    }

    /** Lower median: with gaps [30, 60] (one skipped month) this picks 30, the real period. */
    private static long median(List<Long> values) {
        List<Long> sorted = values.stream().sorted().toList();
        return sorted.get((sorted.size() - 1) / 2);
    }
}
