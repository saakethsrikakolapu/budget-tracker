package com.saaketh.budget.imports.parser;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One statement row after parsing, before it's saved.
 *
 * @param amount negative = money spent, positive = refund or payment
 * @param postedDate may be null
 * @param bankCategory may be null
 */
public record ParsedTransaction(
        LocalDate transactionDate,
        LocalDate postedDate,
        String description,
        BigDecimal amount,
        String bankCategory) {
}
