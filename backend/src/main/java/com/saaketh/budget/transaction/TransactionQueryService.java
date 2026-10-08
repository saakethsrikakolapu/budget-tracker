package com.saaketh.budget.transaction;

import com.saaketh.budget.account.Account;
import com.saaketh.budget.account.AccountService;
import com.saaketh.budget.category.Category;
import com.saaketh.budget.category.CategoryService;
import com.saaketh.budget.common.BadRequestException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Searching and summarizing a user's transactions. */
@Service
public class TransactionQueryService {

    static final int DEFAULT_PAGE_SIZE = 50;
    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_SEARCH_LENGTH = 100;

    /** Value of the category filter that means "transactions with no category". */
    public static final String UNCATEGORIZED = "uncategorized";

    /**
     * All filters are optional; null means "don't filter on this".
     *
     * @param category a category id, or "uncategorized"
     */
    public record Filter(Long accountId, LocalDate from, LocalDate to, String search, String category) {
    }

    /** categoryId/categoryName/categorySource are null for Uncategorized. */
    public record TransactionItem(Long id, Long accountId, String accountName, LocalDate transactionDate,
            LocalDate postedDate, String description, BigDecimal amount, String bankCategory,
            Long categoryId, String categoryName, CategorySource categorySource) {
    }

    /**
     * Totals over every matching transaction, not just the current page. Spending figures only
     * include categories that count as spending (and Uncategorized), so paying the card bill or
     * moving money between accounts doesn't look like spending.
     *
     * @param spent purchases, as a positive number
     * @param refunds money back on purchases (returns), as a positive number
     * @param netSpending spent - refunds
     * @param count all matching transactions
     * @param excludedCount matching transactions in categories that don't count as spending
     */
    public record Totals(BigDecimal spent, BigDecimal refunds, BigDecimal netSpending, long count,
            long excludedCount) {
    }

    public record TransactionPage(List<TransactionItem> items, int page, int size, long totalItems, int totalPages,
            Totals totals) {
    }

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final EntityManager entityManager;

    public TransactionQueryService(TransactionRepository transactionRepository, AccountService accountService,
            CategoryService categoryService, EntityManager entityManager) {
        this.transactionRepository = transactionRepository;
        this.accountService = accountService;
        this.categoryService = categoryService;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public TransactionPage search(Long userId, Filter filter, int page, int size) {
        Specification<Transaction> spec = toSpecification(userId, filter);
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);

        // Newest first; id breaks ties so the order is stable across pages.
        Page<Transaction> result = transactionRepository.findAll(spec,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.desc("transactionDate"), Sort.Order.desc("id"))));

        Map<Long, String> accountNames = accountService.list(userId).stream()
                .collect(Collectors.toMap(Account::getId, Account::getName));
        Map<Long, Category> categories = categoryService.byId(userId);
        List<TransactionItem> items = result.getContent().stream()
                .map(t -> toItem(t, accountNames, categories))
                .toList();

        return new TransactionPage(items, safePage, safeSize, result.getTotalElements(), result.getTotalPages(),
                totals(spec));
    }

    static TransactionItem toItem(Transaction t, Map<Long, String> accountNames, Map<Long, Category> categories) {
        Category category = t.getCategoryId() == null ? null : categories.get(t.getCategoryId());
        return new TransactionItem(t.getId(), t.getAccountId(), accountNames.get(t.getAccountId()),
                t.getTransactionDate(), t.getPostedDate(), t.getDescription(), t.getAmount(), t.getBankCategory(),
                category == null ? null : category.getId(), category == null ? null : category.getName(),
                category == null ? null : t.getCategorySource());
    }

    /** Months that have transactions, newest first, e.g. ["2026-10", "2026-09"]. */
    @Transactional(readOnly = true)
    public List<String> months(Long userId) {
        return transactionRepository.findMonthsWithTransactions(userId).stream()
                .map(row -> "%04d-%02d".formatted(row.year(), row.month()))
                .toList();
    }

    private static Specification<Transaction> toSpecification(Long userId, Filter filter) {
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new BadRequestException("\"from\" must be on or before \"to\"");
        }
        List<Specification<Transaction>> parts = new ArrayList<>();
        parts.add(TransactionSpecifications.belongsTo(userId));
        if (filter.accountId() != null) {
            parts.add(TransactionSpecifications.inAccount(filter.accountId()));
        }
        if (filter.from() != null) {
            parts.add(TransactionSpecifications.onOrAfter(filter.from()));
        }
        if (filter.to() != null) {
            parts.add(TransactionSpecifications.onOrBefore(filter.to()));
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            String search = filter.search().trim();
            if (search.length() > MAX_SEARCH_LENGTH) {
                throw new BadRequestException("Search text must be at most %d characters".formatted(MAX_SEARCH_LENGTH));
            }
            parts.add(TransactionSpecifications.descriptionContains(search));
        }
        if (filter.category() != null && !filter.category().isBlank()) {
            parts.add(categorySpecification(filter.category().trim()));
        }
        return Specification.allOf(parts);
    }

    private static Specification<Transaction> categorySpecification(String category) {
        if (category.equalsIgnoreCase(UNCATEGORIZED)) {
            return TransactionSpecifications.uncategorized();
        }
        try {
            return TransactionSpecifications.inCategory(Long.parseLong(category));
        } catch (NumberFormatException e) {
            throw new BadRequestException("category must be a category id or \"uncategorized\"");
        }
    }

    /**
     * Spending totals use only categories that count as spending; the count covers everything.
     * Sums are computed by Postgres on exact NUMERIC values, never in floating point.
     */
    private Totals totals(Specification<Transaction> spec) {
        Sums spending = sums(spec.and(TransactionSpecifications.countsAsSpending()));
        long all = transactionRepository.count(spec);
        BigDecimal spent = spending.negatives().negate();
        BigDecimal refunds = spending.positives();
        return new Totals(spent, refunds, spent.subtract(refunds), all, all - spending.count());
    }

    private record Sums(BigDecimal negatives, BigDecimal positives, long count) {
    }

    /** One query: SUM of negative amounts, SUM of positive amounts, and COUNT. */
    private Sums sums(Specification<Transaction> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Transaction> root = query.from(Transaction.class);
        Expression<BigDecimal> amount = root.get("amount");

        Expression<BigDecimal> negatives = cb.<BigDecimal>selectCase()
                .when(cb.lessThan(amount, BigDecimal.ZERO), amount).otherwise(BigDecimal.ZERO);
        Expression<BigDecimal> positives = cb.<BigDecimal>selectCase()
                .when(cb.greaterThan(amount, BigDecimal.ZERO), amount).otherwise(BigDecimal.ZERO);

        query.multiselect(
                        cb.coalesce(cb.sum(negatives), BigDecimal.ZERO).alias("negatives"),
                        cb.coalesce(cb.sum(positives), BigDecimal.ZERO).alias("positives"),
                        cb.count(root).alias("count"))
                .where(spec.toPredicate(root, query, cb));

        Tuple row = entityManager.createQuery(query).getSingleResult();
        return new Sums(money(row.get("negatives", BigDecimal.class)), money(row.get("positives", BigDecimal.class)),
                row.get("count", Long.class));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2);
    }
}
