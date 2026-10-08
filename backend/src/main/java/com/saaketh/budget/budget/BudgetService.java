package com.saaketh.budget.budget;

import com.saaketh.budget.category.Category;
import com.saaketh.budget.category.CategoryService;
import com.saaketh.budget.common.BadRequestException;
import com.saaketh.budget.common.NotFoundException;
import com.saaketh.budget.transaction.TransactionRepository;
import com.saaketh.budget.transaction.TransactionRepository.CategoryTotal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BudgetService {

    /**
     * One budgeted category for one month.
     *
     * @param spent net spending: purchases minus refunds (can be negative if refunds exceed purchases)
     * @param remaining limit - spent (negative when over budget)
     * @param percentUsed spent / limit, rounded to a whole percent (never below 0)
     */
    public record BudgetLine(Long categoryId, String categoryName, BigDecimal limit, BigDecimal spent,
            BigDecimal remaining, int percentUsed) {
    }

    /**
     * @param unbudgetedSpending net spending in spending categories without a budget, plus Uncategorized
     */
    public record BudgetOverview(String month, List<BudgetLine> budgets, BigDecimal totalBudgeted,
            BigDecimal totalSpent, BigDecimal unbudgetedSpending) {
    }

    private final BudgetRepository budgetRepository;
    private final CategoryService categoryService;
    private final TransactionRepository transactionRepository;

    public BudgetService(BudgetRepository budgetRepository, CategoryService categoryService,
            TransactionRepository transactionRepository) {
        this.budgetRepository = budgetRepository;
        this.categoryService = categoryService;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public BudgetOverview overview(Long userId, String month) {
        YearMonth yearMonth = parseMonth(month);
        Map<Long, Category> categories = categoryService.byId(userId);
        List<Budget> budgets = budgetRepository.findByUserId(userId);

        // Net spending per category this month (HashMap allows the null key = Uncategorized).
        Map<Long, BigDecimal> spentByCategory = new HashMap<>();
        for (CategoryTotal row : transactionRepository.sumByCategory(userId, yearMonth.atDay(1), yearMonth.atEndOfMonth())) {
            spentByCategory.put(row.categoryId(), row.total().negate());
        }

        List<BudgetLine> lines = budgets.stream()
                .map(b -> line(b, categories.get(b.getCategoryId()), spentByCategory))
                .sorted(Comparator.comparingInt(BudgetLine::percentUsed).reversed()
                        .thenComparing(BudgetLine::categoryName))
                .toList();

        Set<Long> budgeted = budgets.stream().map(Budget::getCategoryId).collect(Collectors.toSet());
        BigDecimal unbudgeted = spentByCategory.entrySet().stream()
                .filter(e -> e.getKey() == null
                        || (!budgeted.contains(e.getKey()) && countsAsSpending(categories.get(e.getKey()))))
                .map(Map.Entry::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new BudgetOverview(yearMonth.toString(), lines,
                money(lines.stream().map(BudgetLine::limit).reduce(BigDecimal.ZERO, BigDecimal::add)),
                money(lines.stream().map(BudgetLine::spent).reduce(BigDecimal.ZERO, BigDecimal::add)),
                money(unbudgeted));
    }

    /** Create or change the monthly limit for a category (an "upsert"). */
    @Transactional
    public void set(Long userId, Long categoryId, BigDecimal monthlyLimit) {
        Category category = categoryService.getOwned(userId, categoryId);
        if (!category.isCountsAsSpending()) {
            throw new BadRequestException("\"%s\" doesn't count as spending, so it can't have a budget"
                    .formatted(category.getName()));
        }
        budgetRepository.findByUserIdAndCategoryId(userId, categoryId)
                .ifPresentOrElse(
                        existing -> existing.setMonthlyLimit(monthlyLimit),
                        () -> budgetRepository.save(new Budget(userId, categoryId, monthlyLimit)));
    }

    @Transactional
    public void delete(Long userId, Long categoryId) {
        Budget budget = budgetRepository.findByUserIdAndCategoryId(userId, categoryId)
                .orElseThrow(() -> new NotFoundException("Budget not found"));
        budgetRepository.delete(budget);
    }

    private static BudgetLine line(Budget budget, Category category, Map<Long, BigDecimal> spentByCategory) {
        BigDecimal limit = budget.getMonthlyLimit();
        BigDecimal spent = money(spentByCategory.getOrDefault(budget.getCategoryId(), BigDecimal.ZERO));
        int percent = spent.signum() <= 0 ? 0
                : spent.multiply(BigDecimal.valueOf(100)).divide(limit, 0, RoundingMode.HALF_UP).intValue();
        return new BudgetLine(budget.getCategoryId(), category == null ? null : category.getName(), limit, spent,
                limit.subtract(spent), percent);
    }

    private static boolean countsAsSpending(Category category) {
        return category != null && category.isCountsAsSpending();
    }

    private static YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new BadRequestException("month must look like 2026-09");
        }
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }
}
