package com.saaketh.budget.budget;

import com.saaketh.budget.budget.BudgetPlanner.Fixed;
import com.saaketh.budget.budget.BudgetPlanner.Kind;
import com.saaketh.budget.budget.BudgetPlanner.Plan;
import com.saaketh.budget.budget.BudgetPlanner.Row;
import com.saaketh.budget.category.Category;
import com.saaketh.budget.category.CategoryService;
import com.saaketh.budget.common.BadRequestException;
import com.saaketh.budget.recurring.RecurringService;
import com.saaketh.budget.recurring.RecurringService.RecurringCharge;
import com.saaketh.budget.transaction.TransactionRepository;
import com.saaketh.budget.transaction.TransactionRepository.PlanningRow;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The suggested budget (see BudgetPlanner for how it's worked out) and "use this budget", which
 * saves it as ordinary per-category budgets.
 */
@Service
public class BudgetPlanService {

    /**
     * Money from people: for students that's often family, so it counts as money coming in.
     * (Money sent the other way, and card payments, stay out of both income and spending.)
     */
    private static final Pattern PERSON_TO_PERSON = Pattern.compile("\\b(ZELLE|VENMO|CASH ?APP)\\b", Pattern.CASE_INSENSITIVE);
    private static final BigDecimal MAX_TARGET = new BigDecimal("1000000");

    /**
     * One category in the plan. categoryId null = Uncategorized (part of the picture, but it can't
     * hold a budget until those transactions get a category).
     *
     * @param pace HIGHER / NORMAL / LOWER than usual by this day of the month, or UNKNOWN
     * @param hasBudget the person already has a budget here (saving the plan won't change it)
     */
    public record PlanLine(Long categoryId, String categoryName, BigDecimal usual, BigDecimal fixed,
            BigDecimal suggested, BigDecimal spent, BigDecimal usualSoFar, String pace, boolean hasBudget) {
    }

    /**
     * @param fullMonths how many full months "usual" is based on (0 = upload more first)
     * @param throughDay the latest day of this month with data (0 = none yet)
     * @param monthlyIncome usual money in per month; null when the statements show none
     * @param target the monthly total the plan is built around (null with no history)
     * @param basis INCOME, USUAL (no income in the statements), CUSTOM (the person's number), or null
     */
    public record BudgetPlan(String month, int fullMonths, int throughDay, BigDecimal monthlyIncome,
            BigDecimal target, String basis, BigDecimal suggestedTotal, BigDecimal usualTotal, BigDecimal spent,
            BigDecimal usualSoFar, String pace, List<PlanLine> lines) {
    }

    private final TransactionRepository transactionRepository;
    private final CategoryService categoryService;
    private final RecurringService recurringService;
    private final BudgetRepository budgetRepository;

    public BudgetPlanService(TransactionRepository transactionRepository, CategoryService categoryService,
            RecurringService recurringService, BudgetRepository budgetRepository) {
        this.transactionRepository = transactionRepository;
        this.categoryService = categoryService;
        this.recurringService = recurringService;
        this.budgetRepository = budgetRepository;
    }

    @Transactional(readOnly = true)
    public BudgetPlan plan(Long userId, String month, BigDecimal target) {
        Map<Long, Category> categories = categoryService.byId(userId);
        Plan plan = compute(userId, parseMonth(month), validTarget(target), categories);
        Set<Long> budgeted = budgetRepository.findByUserId(userId).stream()
                .map(Budget::getCategoryId)
                .collect(Collectors.toSet());

        List<PlanLine> lines = plan.lines().stream()
                .map(l -> new PlanLine(l.categoryId(),
                        l.categoryId() == null ? "Uncategorized" : categories.get(l.categoryId()).getName(),
                        l.usual(), l.fixed(), l.suggested(), l.spent(), l.usualSoFar(), l.pace().name(),
                        budgeted.contains(l.categoryId())))
                .toList();
        return new BudgetPlan(plan.month().toString(), plan.baselineMonths().size(), plan.throughDay(),
                plan.monthlyIncome(), plan.target(), plan.basis() == null ? null : plan.basis().name(),
                plan.suggestedTotal(), plan.usualTotal(), plan.spentTotal(), plan.usualSoFarTotal(), plan.pace().name(),
                lines);
    }

    /**
     * "Use this budget": every suggested category gets that monthly budget. Budgets the person
     * already set are left exactly as they are.
     *
     * @return how many budgets were created
     */
    @Transactional
    public int apply(Long userId, String month, BigDecimal target) {
        Map<Long, Category> categories = categoryService.byId(userId);
        Plan plan = compute(userId, parseMonth(month), validTarget(target), categories);
        Set<Long> budgeted = budgetRepository.findByUserId(userId).stream()
                .map(Budget::getCategoryId)
                .collect(Collectors.toSet());
        int created = 0;
        for (BudgetPlanner.Line line : plan.lines()) {
            Category category = line.categoryId() == null ? null : categories.get(line.categoryId());
            if (category != null && category.isCountsAsSpending() && line.suggested().signum() > 0
                    && !budgeted.contains(line.categoryId())) {
                budgetRepository.save(new Budget(userId, line.categoryId(), line.suggested()));
                created++;
            }
        }
        return created;
    }

    private Plan compute(Long userId, YearMonth month, BigDecimal target, Map<Long, Category> categories) {
        List<Row> rows = transactionRepository.findForPlanning(userId).stream()
                .map(r -> new Row(r.date(), r.amount(), r.categoryId(), kind(r, categories)))
                .toList();
        List<Fixed> fixed = recurringService.overview(userId).charges().stream()
                .filter(RecurringCharge::active)
                .map(c -> new Fixed(c.categoryId(), c.monthlyCost()))
                .toList();
        return BudgetPlanner.plan(rows, fixed, month, target);
    }

    /** Spending, money in, or neither: decided by the category, like the rest of the app. */
    static Kind kind(PlanningRow row, Map<Long, Category> categories) {
        Category category = row.categoryId() == null ? null : categories.get(row.categoryId());
        boolean moneyIn = row.amount().signum() > 0;
        if (moneyIn && category != null && category.getName().equalsIgnoreCase("Income")) {
            return Kind.INCOME;
        }
        if (moneyIn && (category == null || !category.isCountsAsSpending())
                && PERSON_TO_PERSON.matcher(row.description()).find()) {
            return Kind.INCOME;
        }
        if (category == null || category.isCountsAsSpending()) {
            return Kind.SPENDING;
        }
        return Kind.OTHER;
    }

    private static BigDecimal validTarget(BigDecimal target) {
        if (target == null) {
            return null;
        }
        if (target.signum() <= 0 || target.compareTo(MAX_TARGET) > 0 || target.stripTrailingZeros().scale() > 2) {
            throw new BadRequestException("The monthly amount must be between $0.01 and $1,000,000");
        }
        return target;
    }

    private static YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new BadRequestException("month must look like 2026-09");
        }
    }
}
