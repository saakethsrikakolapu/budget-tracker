package com.saaketh.budget.transaction;

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

    /** Case-insensitive "contains". % and _ in the search text are matched literally, not as wildcards. */
    public static Specification<Transaction> descriptionContains(String text) {
        String pattern = "%" + escapeLike(text.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("description")), pattern, '\\');
    }

    /** In SQL LIKE, % means "anything" and _ means "any one character"; escape them (and the escape char). */
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
