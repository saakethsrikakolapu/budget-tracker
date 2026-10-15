package com.saaketh.budget.transaction;

/** How a transaction got its category. Stored as text in transactions.category_source. */
public enum CategorySource {
    /** Mapped from the bank's own label, e.g. Capital One "Dining" -> "Food & Dining". */
    BANK,
    /** Matched one of the user's rules, e.g. description contains "POSHMARK" -> Shopping. */
    RULE,
    /** Matched the built-in merchant dictionary, e.g. "PIZZA" -> Food & Dining. */
    KEYWORD,
    /** Guessed by the machine-learning model (CategoryModel) when nothing else matched; shown as "Suggested". */
    SUGGESTED,
    /** Chosen by the user. Automation never overwrites it. */
    MANUAL
}
