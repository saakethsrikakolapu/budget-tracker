package com.saaketh.budget.report;

import com.saaketh.budget.category.Category;
import com.saaketh.budget.category.CategoryService;
import com.saaketh.budget.common.BadRequestException;
import com.saaketh.budget.transaction.TransactionQueryService;
import com.saaketh.budget.transaction.TransactionQueryService.Filter;
import com.saaketh.budget.transaction.TransactionQueryService.Totals;
import com.saaketh.budget.transaction.TransactionRepository;
import com.saaketh.budget.transaction.TransactionRepository.CategoryTotal;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Everything the dashboard shows for one month, in one request. */
@Service
public class DashboardService {

    static final int TREND_MONTHS = 6;

    /** categoryId is null for Uncategorized. amount = net spending (purchases minus refunds). */
    public record CategorySpending(Long categoryId, String categoryName, BigDecimal amount) {
    }

    public record MonthSpending(String month, BigDecimal netSpending) {
    }

    /**
     * @param spendingByCategory biggest first; only categories with net spending above zero
     * @param monthlyTrend the selected month and the {@value #TREND_MONTHS}-1 before it, oldest first
     */
    public record Dashboard(String month, Totals totals, List<CategorySpending> spendingByCategory,
            List<MonthSpending> monthlyTrend) {
    }

    private final TransactionRepository transactionRepository;
    private final TransactionQueryService transactionQueryService;
    private final CategoryService categoryService;

    public DashboardService(TransactionRepository transactionRepository,
            TransactionQueryService transactionQueryService, CategoryService categoryService) {
        this.transactionRepository = transactionRepository;
        this.transactionQueryService = transactionQueryService;
        this.categoryService = categoryService;
    }

    @Transactional(readOnly = true)
    public Dashboard forMonth(Long userId, String month) {
        YearMonth selected = parseMonth(month);
        Map<Long, Category> categories = categoryService.byId(userId);

        // Same totals as the transaction list shows for this month (one source of truth).
        Totals totals = transactionQueryService.search(userId,
                new Filter(null, selected.atDay(1), selected.atEndOfMonth(), null, null), 0, 1).totals();

        List<CategorySpending> byCategory = spendingByCategory(userId, selected, categories).stream()
                .filter(c -> c.amount().signum() > 0)
                .sorted(Comparator.comparing(CategorySpending::amount).reversed())
                .toList();

        List<MonthSpending> trend = new ArrayList<>();
        for (int i = TREND_MONTHS - 1; i >= 0; i--) {
            YearMonth m = selected.minusMonths(i);
            BigDecimal net = spendingByCategory(userId, m, categories).stream()
                    .map(CategorySpending::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            trend.add(new MonthSpending(m.toString(), net.setScale(2)));
        }

        return new Dashboard(selected.toString(), totals, byCategory, trend);
    }

    /** Net spending per category for one month, leaving out categories that don't count as spending. */
    private List<CategorySpending> spendingByCategory(Long userId, YearMonth month, Map<Long, Category> categories) {
        List<CategorySpending> result = new ArrayList<>();
        for (CategoryTotal row : transactionRepository.sumByCategory(userId, month.atDay(1), month.atEndOfMonth())) {
            Category category = row.categoryId() == null ? null : categories.get(row.categoryId());
            if (category != null && !category.isCountsAsSpending()) {
                continue;
            }
            result.add(new CategorySpending(row.categoryId(), category == null ? "Uncategorized" : category.getName(),
                    row.total().negate().setScale(2)));
        }
        return result;
    }

    private static YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new BadRequestException("month must look like 2026-09");
        }
    }
}
