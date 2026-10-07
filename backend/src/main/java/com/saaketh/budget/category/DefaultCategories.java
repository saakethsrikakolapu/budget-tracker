package com.saaketh.budget.category;

import java.util.List;

/**
 * The categories every new user starts with. Keep in sync with the backfill list in
 * V5__create_categories.sql (which gave them to users who existed before categories did).
 */
final class DefaultCategories {

    private DefaultCategories() {
    }

    record Default(String name, boolean countsAsSpending) {
    }

    static final List<Default> ALL = List.of(
            new Default("Food & Dining", true),
            new Default("Groceries", true),
            new Default("Shopping", true),
            new Default("Transportation", true),
            new Default("Travel", true),
            new Default("Entertainment", true),
            new Default("Subscriptions", true),
            new Default("Bills & Utilities", true),
            new Default("Education", true),
            new Default("Health & Personal Care", true),
            new Default("Fees & Interest", true),
            new Default("Income", false),
            new Default("Payments & Transfers", false),
            new Default("Other", true));
}
