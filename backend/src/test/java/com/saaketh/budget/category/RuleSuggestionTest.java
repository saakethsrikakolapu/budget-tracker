package com.saaketh.budget.category;

import static org.assertj.core.api.Assertions.assertThat;

import com.saaketh.budget.category.CategoryRules.Suggestion;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Picking the merchant text for the "Always put ... in ...?" prompt. */
class RuleSuggestionTest {

    private static final List<String> HISTORY = normalized(
            "DEBIT CARD PURCHASE STREAMFLIX",
            "Debit Card Purchase - STREAMFLIX LOS GATOS CA",
            "PURCHASE AUTHORIZED ON 10/21 STREAMFLIX CARD 1234",
            "STREAMFLIX.COM 5 MOVIE BLVD LOS GATOS 95032 CA USA",
            "HARRIS TEETER #0318",
            "HARRIS TEETER #0318",
            "HARRIS TEETER #0412",
            "BLUE BOTTLE COFFEE",
            "UBER   TRIP 1234",
            "UBER EATS ORDER");

    @Test
    void oneMerchantWrittenFourWaysGetsOnePattern() {
        // Every format from the screenshot suggests the same text, which matches all four.
        for (String description : List.of(
                "DEBIT CARD PURCHASE STREAMFLIX",
                "Debit Card Purchase - STREAMFLIX LOS GATOS CA",
                "PURCHASE AUTHORIZED ON 10/21 STREAMFLIX CARD 1234",
                "STREAMFLIX.COM 5 MOVIE BLVD LOS GATOS 95032 CA USA")) {
            assertThat(CategoryRules.suggest(description, HISTORY)).isEqualTo(new Suggestion("STREAMFLIX", 4));
        }
    }

    @Test
    void prefersTheMoreSpecificTextWhenItMatchesJustAsMany() {
        // "HARRIS" and "HARRIS TEETER" match the same 3, so the clearer one wins; store numbers go.
        assertThat(CategoryRules.suggest("HARRIS TEETER #0318", HISTORY))
                .isEqualTo(new Suggestion("HARRIS TEETER", 3));
        // A merchant seen once keeps its full name rather than a vague first word.
        assertThat(CategoryRules.suggest("BLUE BOTTLE COFFEE", HISTORY))
                .isEqualTo(new Suggestion("BLUE BOTTLE COFFEE", 1));
    }

    @Test
    void broaderTextWinsWhenItCatchesMore() {
        // "UBER" covers trips and Eats; the user sees the text in the prompt and can say "Just this one".
        assertThat(CategoryRules.suggest("UBER   TRIP 1234", HISTORY)).isEqualTo(new Suggestion("UBER", 2));
    }

    @Test
    void fallsBackToTheDescriptionWhenThereAreNoMerchantWords() {
        assertThat(CategoryRules.suggest("  12345 / 678 ", HISTORY)).isEqualTo(new Suggestion("12345 / 678", 0));
    }

    private static List<String> normalized(String... descriptions) {
        return List.of(descriptions).stream().map(CategoryRules::normalize).toList();
    }
}
