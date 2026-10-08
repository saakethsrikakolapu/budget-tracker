package com.saaketh.budget.category;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Plain unit tests for rule matching: no Spring, no database. */
class CategoryRulesTest {

    @Test
    void normalizesCaseAndWhitespace() {
        assertThat(CategoryRules.normalize("  Uber \t eats ")).isEqualTo("UBER EATS");
    }

    @Test
    void longestPatternWins() {
        CategoryRule uber = new CategoryRule(1L, "UBER", 10L);
        CategoryRule uberEats = new CategoryRule(1L, "UBER EATS", 20L);
        List<CategoryRule> sorted = List.of(uber, uberEats).stream().sorted(CategoryRules.MOST_SPECIFIC_FIRST).toList();

        assertThat(CategoryRules.firstMatch(sorted, "UBER   Eats Pending").orElseThrow().getCategoryId()).isEqualTo(20L);
        assertThat(CategoryRules.firstMatch(sorted, "UBER TRIP 1234").orElseThrow().getCategoryId()).isEqualTo(10L);
        assertThat(CategoryRules.firstMatch(sorted, "LYFT RIDE")).isEmpty();
    }

    @Test
    void matchesAnywhereInTheDescription() {
        List<CategoryRule> rules = List.of(new CategoryRule(1L, "POSHMARK", 5L));

        assertThat(CategoryRules.firstMatch(rules, "PAYPAL *Poshmark Inc")).isPresent();
    }
}
