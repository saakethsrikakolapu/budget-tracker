package com.saaketh.budget.transaction;

import com.saaketh.budget.category.Category;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * Reusable filter pieces for transaction queries. Each one becomes part of the SQL WHERE clause,
 * and they're combined only for the filters the user actually set. User input is always passed
 * as a query parameter, never pasted into SQL text, so it can't cause SQL injection.
 */
public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    /** Always applied: a user only ever sees their own transactions. */
    public static Specification<Transaction> belongsTo(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("userId"), userId);
    }

    public static Specification<Transaction> inAccount(Long accountId) {
        return (root, query, cb) -> cb.equal(root.get("accountId"), accountId);
    }

    public static Specification<Transaction> onOrAfter(LocalDate from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("transactionDate"), from);
    }

    public static Specification<Transaction> onOrBefore(LocalDate to) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("transactionDate"), to);
    }

    public static Specification<Transaction> inCategory(Long categoryId) {
        return (root, query, cb) -> cb.equal(root.get("categoryId"), categoryId);
    }

    public static Specification<Transaction> uncategorized() {
        return (root, query, cb) -> cb.isNull(root.get("categoryId"));
    }

    /**
     * Uncategorized, or in a category that counts as spending. Written as
     * "NOT EXISTS (a category with this id that does NOT count as spending)".
     */
    public static Specification<Transaction> countsAsSpending() {
        return (root, query, cb) -> {
            Subquery<Long> excluded = query.subquery(Long.class);
            Root<Category> category = excluded.from(Category.class);
            excluded.select(category.get("id")).where(
                    cb.equal(category.get("id"), root.get("categoryId")),
                    cb.isFalse(category.get("countsAsSpending")));
            return cb.not(cb.exists(excluded));
        };
    }

    /**
     * Case-insensitive "contains" that also ignores extra spaces (like rules do), so "uber trip"
     * finds "UBER   TRIP". % and _ in the search text are matched literally, not as wildcards.
     */
    public static Specification<Transaction> descriptionContains(String text) {
        String collapsed = text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        String pattern = "%" + escapeLike(collapsed) + "%";
        return (root, query, cb) -> {
            Expression<String> description = cb.function("regexp_replace", String.class,
                    cb.lower(root.get("description")), cb.literal("\\s+"), cb.literal(" "), cb.literal("g"));
            return cb.like(description, pattern, '\\');
        };
    }

    /** In SQL LIKE, % means "anything" and _ means "any one character"; escape them (and the escape char). */
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
