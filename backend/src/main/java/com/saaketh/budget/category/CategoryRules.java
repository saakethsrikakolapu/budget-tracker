package com.saaketh.budget.category;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Pure matching logic for rules (no database), so it's easy to unit test. */
public final class CategoryRules {

    private CategoryRules() {
    }

    /** "  Uber   eats " -> "UBER EATS". Applied to both patterns and descriptions. */
    public static String normalize(String text) {
        return text.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    /**
     * Most specific first: the longest pattern wins, so "UBER EATS" beats "UBER". Ties go to the
     * newest rule (it reflects the user's latest intent); id breaks any remaining tie.
     */
    static final Comparator<CategoryRule> MOST_SPECIFIC_FIRST =
            Comparator.comparingInt((CategoryRule r) -> r.getPattern().length()).reversed()
                    .thenComparing(CategoryRule::getCreatedAt, Comparator.reverseOrder())
                    .thenComparing(CategoryRule::getId, Comparator.nullsLast(Comparator.reverseOrder()));

    /** @param rulesMostSpecificFirst already sorted with {@link #MOST_SPECIFIC_FIRST} */
    static Optional<CategoryRule> firstMatch(List<CategoryRule> rulesMostSpecificFirst, String description) {
        String normalized = normalize(description);
        return rulesMostSpecificFirst.stream().filter(r -> normalized.contains(r.getPattern())).findFirst();
    }
}
