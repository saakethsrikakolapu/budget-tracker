package com.saaketh.budget.budget;

import static org.assertj.core.api.Assertions.assertThat;

import com.saaketh.budget.budget.BudgetPlanner.Basis;
import com.saaketh.budget.budget.BudgetPlanner.Fixed;
import com.saaketh.budget.budget.BudgetPlanner.Kind;
import com.saaketh.budget.budget.BudgetPlanner.Line;
import com.saaketh.budget.budget.BudgetPlanner.Pace;
import com.saaketh.budget.budget.BudgetPlanner.Plan;
import com.saaketh.budget.budget.BudgetPlanner.Row;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The suggested budget, worked out from made-up histories. */
class BudgetPlannerTest {

    private static final long FOOD = 1;
    private static final long GROCERIES = 2;
    private static final long SHOPPING = 3;
    private static final long FUN = 4;
    private static final long BILLS = 5;
    private static final long TRAVEL = 6;
    private static final YearMonth OCT = YearMonth.of(2026, 10);

    private final List<Row> rows = new ArrayList<>();

    @Test
    void onlyFullMonthsCountAndAtMostSix() {
        // Data from Mar 18 to Oct 12: March is half a month, October is the month being planned.
        spend("2026-03-18", FOOD, "10");
        spend("2026-10-12", FOOD, "10");

        assertThat(BudgetPlanner.baselineMonths(rows, OCT))
                .containsExactly(YearMonth.of(2026, 4), YearMonth.of(2026, 5), YearMonth.of(2026, 6),
                        YearMonth.of(2026, 7), YearMonth.of(2026, 8), YearMonth.of(2026, 9));

        // A few quiet days at either end still count as a full month.
        rows.clear();
        spend("2026-08-04", FOOD, "10");
        spend("2026-09-27", FOOD, "10");
        assertThat(BudgetPlanner.baselineMonths(rows, OCT))
                .containsExactly(YearMonth.of(2026, 8), YearMonth.of(2026, 9));
    }

    @Test
    void usualIsTheMedianSoOneBigPurchaseDoesntMoveIt() {
        everyMonth(7, 9, FOOD, "120");
        spend("2026-07-10", SHOPPING, "40");
        spend("2026-08-10", SHOPPING, "55");
        spend("2026-09-10", SHOPPING, "900"); // a laptop
        spend("2026-10-02", FOOD, "5");

        Plan plan = plan(null);

        assertThat(line(plan, SHOPPING).usual()).isEqualByComparingTo("55");
        assertThat(line(plan, FOOD).usual()).isEqualByComparingTo("120");
    }

    @Test
    void aMonthWithNothingInACategoryCountsAsZero() {
        everyMonth(7, 9, FOOD, "100");
        spend("2026-08-20", TRAVEL, "300"); // one trip in three months
        spend("2026-10-02", FOOD, "5");

        Plan plan = plan(null);

        // Usual travel is $0 (median of 0, 300, 0), so it isn't in the plan at all...
        assertThat(plan.lines()).noneMatch(l -> l.categoryId() == TRAVEL);

        // ...and a trip this month stands out instead of looking normal.
        spend("2026-10-05", TRAVEL, "250");
        Line travel = line(plan(null), TRAVEL);
        assertThat(travel.usual()).isEqualByComparingTo("0");
        assertThat(travel.suggested()).isEqualByComparingTo("0");
        assertThat(travel.pace()).isEqualTo(Pace.HIGHER);
    }

    @Test
    void withIncomeBillsKeepTheirCostAndEverythingElseIsTrimmedEqually() {
        // Earns $850, usually spends $950: $150 of bills, $800 of everything else.
        everyMonth(7, 9, FOOD, "300");
        everyMonth(7, 9, GROCERIES, "250");
        everyMonth(7, 9, SHOPPING, "150");
        everyMonth(7, 9, FUN, "100");
        everyMonth(7, 9, BILLS, "150");
        for (int month = 7; month <= 9; month++) {
            income("2026-%02d-15".formatted(month), "850");
        }
        spend("2026-10-02", FOOD, "5");

        Plan plan = BudgetPlanner.plan(rows, List.of(new Fixed(BILLS, new BigDecimal("150"))), OCT, null);

        assertThat(plan.basis()).isEqualTo(Basis.INCOME);
        assertThat(plan.monthlyIncome()).isEqualByComparingTo("850");
        assertThat(plan.target()).isEqualByComparingTo("850");
        // $700 left after bills for $800 of usual spending: everything flexible x 0.875.
        assertThat(line(plan, BILLS).suggested()).isEqualByComparingTo("150");
        assertThat(line(plan, FOOD).suggested()).isEqualByComparingTo("262");      // 262.50
        assertThat(line(plan, GROCERIES).suggested()).isEqualByComparingTo("218"); // 218.75
        assertThat(line(plan, SHOPPING).suggested()).isEqualByComparingTo("131");  // 131.25
        assertThat(line(plan, FUN).suggested()).isEqualByComparingTo("87");        // 87.50
        assertThat(plan.suggestedTotal()).isLessThanOrEqualTo(plan.target());
    }

    @Test
    void spendingLessThanYouEarnIsNeverScaledUp() {
        everyMonth(7, 9, FOOD, "200");
        for (int month = 7; month <= 9; month++) {
            income("2026-%02d-15".formatted(month), "1000");
        }
        spend("2026-10-02", FOOD, "5");

        Plan plan = plan(null);

        assertThat(plan.target()).isEqualByComparingTo("1000");
        assertThat(line(plan, FOOD).suggested()).isEqualByComparingTo("200"); // the rest is savings
    }

    @Test
    void withoutIncomeTheTargetIsUsualSpendingAndThePersonCanChangeIt() {
        // A credit card only: no money in. Usual spending $395, rounded up to $400.
        everyMonth(7, 9, FOOD, "300");
        everyMonth(7, 9, SHOPPING, "95");
        spend("2026-10-02", FOOD, "5");

        Plan usual = plan(null);
        assertThat(usual.basis()).isEqualTo(Basis.USUAL);
        assertThat(usual.monthlyIncome()).isNull();
        assertThat(usual.target()).isEqualByComparingTo("400");
        assertThat(line(usual, FOOD).suggested()).isEqualByComparingTo("300"); // nothing trimmed
        assertThat(line(usual, SHOPPING).suggested()).isEqualByComparingTo("95");

        Plan custom = plan(new BigDecimal("300"));
        assertThat(custom.basis()).isEqualTo(Basis.CUSTOM);
        // 300 / 395 of each: 227.84 and 72.15, rounded down to whole dollars.
        assertThat(line(custom, FOOD).suggested()).isEqualByComparingTo("227");
        assertThat(line(custom, SHOPPING).suggested()).isEqualByComparingTo("72");
    }

    @Test
    void cardPaymentsAndTransfersAreNotIncome() {
        everyMonth(7, 9, FOOD, "100");
        for (int month = 7; month <= 9; month++) {
            rows.add(new Row(LocalDate.parse("2026-%02d-20".formatted(month)), new BigDecimal("100"), 9L, Kind.OTHER));
        }
        spend("2026-10-02", FOOD, "5");

        assertThat(plan(null).monthlyIncome()).isNull();
    }

    @Test
    void paceComparesWithTheSameDayInPastMonths() {
        // Rent-like bill on the 1st, food spread over the month.
        for (int month = 7; month <= 9; month++) {
            spend("2026-%02d-01".formatted(month), BILLS, "500");
            spend("2026-%02d-08".formatted(month), FOOD, "40");
            spend("2026-%02d-25".formatted(month), FOOD, "80");
        }
        // October so far (data through the 12th): the bill as usual, but food is way up.
        spend("2026-10-01", BILLS, "500");
        spend("2026-10-09", FOOD, "95");
        spend("2026-10-12", FOOD, "5");

        Plan plan = plan(null);

        assertThat(plan.throughDay()).isEqualTo(12);
        // By the 12th you've usually paid the bill: not "higher" just because it came early.
        assertThat(line(plan, BILLS).pace()).isEqualTo(Pace.NORMAL);
        // Food: usually $40 by the 12th, now $100.
        assertThat(line(plan, FOOD).usualSoFar()).isEqualByComparingTo("40");
        assertThat(line(plan, FOOD).spent()).isEqualByComparingTo("100");
        assertThat(line(plan, FOOD).pace()).isEqualTo(Pace.HIGHER);
    }

    @Test
    void smallDifferencesAreNotFlagged() {
        assertThat(BudgetPlanner.pace(new BigDecimal("8"), new BigDecimal("4"))).isEqualTo(Pace.NORMAL);   // +100%, only $4
        assertThat(BudgetPlanner.pace(new BigDecimal("515"), new BigDecimal("500"))).isEqualTo(Pace.NORMAL); // +$15
        assertThat(BudgetPlanner.pace(new BigDecimal("640"), new BigDecimal("500"))).isEqualTo(Pace.HIGHER);
        assertThat(BudgetPlanner.pace(new BigDecimal("50"), new BigDecimal("100"))).isEqualTo(Pace.LOWER);
    }

    @Test
    void notEnoughHistoryMeansNoSuggestionYet() {
        // Only half of September and part of October.
        spend("2026-09-19", FOOD, "30");
        spend("2026-10-12", FOOD, "10");

        Plan plan = plan(null);

        assertThat(plan.baselineMonths()).isEmpty();
        assertThat(plan.target()).isNull();
        assertThat(plan.pace()).isEqualTo(Pace.UNKNOWN);
        assertThat(line(plan, FOOD).spent()).isEqualByComparingTo("10");
    }

    @Test
    void refundsReduceSpending() {
        everyMonth(7, 9, SHOPPING, "100");
        rows.add(new Row(LocalDate.parse("2026-09-28"), new BigDecimal("100"), SHOPPING, Kind.SPENDING)); // returned
        spend("2026-10-02", FOOD, "5");

        // July 100, August 100, September 0: median 100.
        assertThat(line(plan(null), SHOPPING).usual()).isEqualByComparingTo("100");
    }

    // --- helpers ---

    private Plan plan(BigDecimal target) {
        return BudgetPlanner.plan(rows, List.of(), OCT, target);
    }

    private static Line line(Plan plan, long categoryId) {
        return plan.lines().stream().filter(l -> l.categoryId() == categoryId).findFirst().orElseThrow();
    }

    private void spend(String date, long categoryId, String amount) {
        rows.add(new Row(LocalDate.parse(date), new BigDecimal(amount).negate(), categoryId, Kind.SPENDING));
    }

    private void income(String date, String amount) {
        rows.add(new Row(LocalDate.parse(date), new BigDecimal(amount), 99L, Kind.INCOME));
    }

    /** The same amount once in each month from {@code from} to {@code to} (2026), on the 10th. */
    private void everyMonth(int from, int to, long categoryId, String amount) {
        for (int month = from; month <= to; month++) {
            spend("2026-%02d-10".formatted(month), categoryId, amount);
        }
        // Make sure the data covers the whole of each month.
        spend("2026-%02d-01".formatted(from), categoryId, "0");
        spend("2026-%02d-28".formatted(to), categoryId, "0");
    }
}
