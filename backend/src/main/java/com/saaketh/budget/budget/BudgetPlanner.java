package com.saaketh.budget.budget;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Suggests a monthly budget from a person's own history, so nobody has to type one in.
 * Pure calculation (no database), like RecurringDetector, so it's easy to test.
 *
 * <ol>
 *   <li><b>Full months only.</b> Statements start and end mid-month; a half month would make
 *       "usual" look too low. A month counts once the data covers it (give or take
 *       {@value #EDGE_DAYS} days at each end, since nobody buys something every single day).</li>
 *   <li><b>Usual = the median</b> of the last {@value #MAX_BASELINE_MONTHS} full months before the
 *       month being planned, per category. A month with nothing in a category counts as $0, and one
 *       big purchase (a laptop) doesn't move the median.</li>
 *   <li><b>Target:</b> what usually comes in (paychecks, Zelle/Venmo from family) if there's any,
 *       otherwise what usually goes out. The person can replace it with their own number.</li>
 *   <li><b>Split:</b> regular bills and subscriptions keep their full cost (they can't easily be
 *       cut); the rest of the target is shared among the other spending in the same proportions as
 *       usual. If usual spending is more than the target, every flexible category is trimmed by the
 *       same percentage. It's never scaled up: money left over is savings.</li>
 *   <li><b>Pace:</b> this month so far vs. what had usually been spent by the same day of the
 *       month, so rent on the 1st doesn't look like overspending on the 2nd.</li>
 * </ol>
 */
final class BudgetPlanner {

    static final int MAX_BASELINE_MONTHS = 6;
    static final int EDGE_DAYS = 5;
    /** "Higher than usual" needs both: at least 25% more, and at least $20 more (no alarms over $4). */
    static final BigDecimal PACE_RATIO = new BigDecimal("1.25");
    static final BigDecimal PACE_MIN_DIFFERENCE = new BigDecimal("20");

    private BudgetPlanner() {
    }

    /** What a transaction means for budgeting. The service decides this from its category. */
    enum Kind {
        /** Counts as spending (purchases, and refunds that reduce it). Category null = Uncategorized. */
        SPENDING,
        /** Money coming in: paychecks, deposits, money from family. */
        INCOME,
        /** Neither: card payments, transfers between your own accounts. */
        OTHER
    }

    enum Pace { HIGHER, NORMAL, LOWER, UNKNOWN }

    enum Basis { INCOME, USUAL, CUSTOM }

    /** One transaction, reduced to what planning needs. amount: negative = money out. */
    record Row(LocalDate date, BigDecimal amount, Long categoryId, Kind kind) {
    }

    /** A regular bill or subscription that's still active, and what it costs per month. */
    record Fixed(Long categoryId, BigDecimal monthlyCost) {
    }

    /**
     * One category's plan. categoryId null = Uncategorized (shown, but it can't hold a budget).
     *
     * @param usual median monthly net spending
     * @param fixed the part of it that's regular bills and subscriptions
     * @param suggested the suggested monthly budget, in whole dollars
     * @param spent net spending in the planned month through {@link Plan#throughDay()}
     * @param usualSoFar what had usually been spent by that day of the month
     */
    record Line(Long categoryId, BigDecimal usual, BigDecimal fixed, BigDecimal suggested, BigDecimal spent,
            BigDecimal usualSoFar, Pace pace) {
    }

    /**
     * @param baselineMonths the full months "usual" comes from (empty = not enough data yet)
     * @param throughDay the last day of the planned month that has data (0 = none yet)
     * @param monthlyIncome usual money in per month, or null when the statements show none
     *     (a credit card only)
     * @param target the monthly total the budget is built around; null without any history
     */
    record Plan(YearMonth month, List<YearMonth> baselineMonths, int throughDay, BigDecimal monthlyIncome,
            BigDecimal target, Basis basis, List<Line> lines, Pace pace) {

        BigDecimal suggestedTotal() {
            return sum(lines, Line::suggested);
        }

        BigDecimal usualTotal() {
            return sum(lines, Line::usual);
        }

        BigDecimal spentTotal() {
            return sum(lines, Line::spent);
        }

        BigDecimal usualSoFarTotal() {
            return sum(lines, Line::usualSoFar);
        }
    }

    /**
     * @param customTarget the person's own monthly total, or null to suggest one
     */
    static Plan plan(List<Row> rows, List<Fixed> fixedCosts, YearMonth month, BigDecimal customTarget) {
        List<YearMonth> baseline = baselineMonths(rows, month);
        int throughDay = throughDay(rows, month);

        // Every category that matters: spent in the baseline or this month, or has a regular bill.
        Set<Long> categoryIds = new LinkedHashSet<>();
        for (Row row : rows) {
            YearMonth rowMonth = YearMonth.from(row.date());
            if (row.kind() == Kind.SPENDING && (rowMonth.equals(month) || baseline.contains(rowMonth))) {
                categoryIds.add(row.categoryId());
            }
        }
        Map<Long, BigDecimal> fixedByCategory = new HashMap<>();
        for (Fixed fixed : fixedCosts) {
            fixedByCategory.merge(fixed.categoryId(), fixed.monthlyCost(), BigDecimal::add);
            categoryIds.add(fixed.categoryId());
        }

        // Usual and so-far per category, from net spending per (category, month).
        record Draft(Long categoryId, BigDecimal usual, BigDecimal fixed, BigDecimal spent, BigDecimal usualSoFar) {
        }
        List<Draft> drafts = new ArrayList<>();
        for (Long categoryId : categoryIds) {
            List<BigDecimal> fullMonths = new ArrayList<>();
            List<BigDecimal> soFar = new ArrayList<>();
            for (YearMonth m : baseline) {
                fullMonths.add(floorAtZero(netSpending(rows, categoryId, m, m.lengthOfMonth())));
                soFar.add(floorAtZero(netSpending(rows, categoryId, m, sameDay(throughDay, month, m))));
            }
            BigDecimal usual = median(fullMonths);
            BigDecimal fixed = money(fixedByCategory.getOrDefault(categoryId, BigDecimal.ZERO));
            BigDecimal spent = money(netSpending(rows, categoryId, month, throughDay));
            drafts.add(new Draft(categoryId, usual, fixed, spent, median(soFar)));
        }

        // The total to plan around.
        BigDecimal income = baseline.isEmpty() ? null : usualIncome(rows, baseline);
        BigDecimal usualTotal = drafts.stream().map(d -> d.usual().max(d.fixed())).reduce(BigDecimal.ZERO, BigDecimal::add);
        Basis basis;
        BigDecimal target;
        if (customTarget != null) {
            basis = Basis.CUSTOM;
            target = money(customTarget);
        } else if (baseline.isEmpty()) {
            basis = null;
            target = null;
        } else if (income != null) {
            basis = Basis.INCOME;
            target = roundToTen(income, RoundingMode.DOWN); // round toward spending less
        } else {
            basis = Basis.USUAL;
            target = roundToTen(usualTotal, RoundingMode.CEILING); // up, so nothing usual gets trimmed
        }

        // Split it: bills first, the rest shared by usual proportions (never scaled up).
        BigDecimal totalFixed = drafts.stream().map(Draft::fixed).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalFlexible = drafts.stream()
                .map(d -> d.usual().max(d.fixed()).subtract(d.fixed()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal scale = BigDecimal.ZERO;
        if (target != null && totalFlexible.signum() > 0) {
            scale = target.subtract(totalFixed).divide(totalFlexible, 6, RoundingMode.DOWN)
                    .max(BigDecimal.ZERO).min(BigDecimal.ONE);
        }

        boolean canCompare = !baseline.isEmpty() && throughDay > 0;
        List<Line> lines = new ArrayList<>();
        for (Draft d : drafts) {
            BigDecimal flexible = d.usual().max(d.fixed()).subtract(d.fixed());
            BigDecimal suggested = target == null ? BigDecimal.ZERO.setScale(2)
                    : d.fixed().setScale(0, RoundingMode.CEILING)
                            .add(flexible.multiply(scale).setScale(0, RoundingMode.DOWN))
                            .setScale(2);
            Pace pace = canCompare ? pace(d.spent(), d.usualSoFar()) : Pace.UNKNOWN;
            lines.add(new Line(d.categoryId(), d.usual(), d.fixed(), suggested, d.spent(), d.usualSoFar(), pace));
        }
        lines.sort(Comparator.comparing(Line::suggested).reversed()
                .thenComparing(Comparator.comparing(Line::usual).reversed())
                .thenComparing(Line::spent, Comparator.reverseOrder()));
        lines.removeIf(l -> l.usual().signum() == 0 && l.fixed().signum() == 0 && l.spent().signum() <= 0);

        Pace overall = canCompare ? pace(sum(lines, Line::spent), sum(lines, Line::usualSoFar)) : Pace.UNKNOWN;
        return new Plan(month, baseline, throughDay, income, target, basis, List.copyOf(lines), overall);
    }

    /** Full months before {@code month}, newest last, at most {@value #MAX_BASELINE_MONTHS}. */
    static List<YearMonth> baselineMonths(List<Row> rows, YearMonth month) {
        if (rows.isEmpty()) {
            return List.of();
        }
        LocalDate first = rows.stream().map(Row::date).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate last = rows.stream().map(Row::date).max(Comparator.naturalOrder()).orElseThrow();
        List<YearMonth> full = new ArrayList<>();
        for (YearMonth m = YearMonth.from(first); m.isBefore(month) && !m.isAfter(YearMonth.from(last)); m = m.plusMonths(1)) {
            boolean coversStart = !first.isAfter(m.atDay(EDGE_DAYS));
            boolean coversEnd = !last.isBefore(m.atEndOfMonth().minusDays(EDGE_DAYS - 1));
            if (coversStart && coversEnd) {
                full.add(m);
            }
        }
        return List.copyOf(full.subList(Math.max(0, full.size() - MAX_BASELINE_MONTHS), full.size()));
    }

    /** How far into {@code month} the data goes: its last day if the data continues past it. */
    private static int throughDay(List<Row> rows, YearMonth month) {
        LocalDate last = rows.stream().map(Row::date).max(Comparator.naturalOrder()).orElse(null);
        if (last == null || YearMonth.from(last).isBefore(month)) {
            return 0;
        }
        return YearMonth.from(last).equals(month) ? last.getDayOfMonth() : month.lengthOfMonth();
    }

    /** The same point in another month: day 12 is day 12, but "the whole month" stays the whole month. */
    private static int sameDay(int throughDay, YearMonth planned, YearMonth other) {
        return throughDay >= planned.lengthOfMonth() ? other.lengthOfMonth() : Math.min(throughDay, other.lengthOfMonth());
    }

    /** Purchases minus refunds for one category in one month, from day 1 through {@code day}. */
    private static BigDecimal netSpending(List<Row> rows, Long categoryId, YearMonth month, int day) {
        BigDecimal total = BigDecimal.ZERO;
        for (Row row : rows) {
            if (row.kind() == Kind.SPENDING && Objects.equals(row.categoryId(), categoryId)
                    && YearMonth.from(row.date()).equals(month) && row.date().getDayOfMonth() <= day) {
                total = total.subtract(row.amount());
            }
        }
        return total;
    }

    /** Median money in per baseline month; null if nothing ever came in (e.g. a credit card only). */
    private static BigDecimal usualIncome(List<Row> rows, List<YearMonth> baseline) {
        List<BigDecimal> perMonth = new ArrayList<>();
        for (YearMonth m : baseline) {
            BigDecimal in = BigDecimal.ZERO;
            for (Row row : rows) {
                if (row.kind() == Kind.INCOME && row.amount().signum() > 0 && YearMonth.from(row.date()).equals(m)) {
                    in = in.add(row.amount());
                }
            }
            perMonth.add(in);
        }
        BigDecimal median = median(perMonth);
        return median.signum() > 0 ? median : null;
    }

    static Pace pace(BigDecimal spent, BigDecimal usualSoFar) {
        BigDecimal difference = spent.subtract(usualSoFar);
        if (difference.compareTo(PACE_MIN_DIFFERENCE) >= 0 && spent.compareTo(usualSoFar.multiply(PACE_RATIO)) >= 0) {
            return Pace.HIGHER;
        }
        if (difference.negate().compareTo(PACE_MIN_DIFFERENCE) >= 0
                && usualSoFar.compareTo(spent.multiply(PACE_RATIO)) >= 0) {
            return Pace.LOWER;
        }
        return Pace.NORMAL;
    }

    static BigDecimal median(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO.setScale(2);
        }
        List<BigDecimal> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        BigDecimal median = sorted.size() % 2 == 1 ? sorted.get(middle)
                : sorted.get(middle - 1).add(sorted.get(middle)).divide(BigDecimal.TWO, 2, RoundingMode.HALF_UP);
        return money(median);
    }

    private static BigDecimal roundToTen(BigDecimal amount, RoundingMode mode) {
        return amount.divide(BigDecimal.TEN, 0, mode).multiply(BigDecimal.TEN).setScale(2);
    }

    private static BigDecimal floorAtZero(BigDecimal amount) {
        return amount.max(BigDecimal.ZERO);
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(List<Line> lines, java.util.function.Function<Line, BigDecimal> field) {
        return lines.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
    }
}
