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

    @Test
    void paymentAppsWithoutAStoreSuggestTheAppNotTheLinkingWords() {
        // Shapes from a real checking export, with made-up names. Before: "FROM" was offered, which
        // would also have caught every Zelle and Cash App line.
        List<String> history = normalized(
                "Withdrawal from PAYPAL to JANE DOE PURCHASE",
                "Withdrawal from PAYPAL to JANE DOE PURCHASE",
                "Deposit from PAYPAL from JANE DOE TRANSFER",
                "Deposit from Cash App Jane Doe",
                "Zelle money received from JOHN ROE",
                "Digital Card Purchase - DEPOP P476922841744 8449353879 NY",
                "Debit Card Purchase - DEPOP P470816315920 8449353879 NY",
                "PAYPAL *STEAMGAMES 4029357733");

        assertThat(CategoryRules.suggest("Withdrawal from PAYPAL to JANE DOE PURCHASE", history))
                .isEqualTo(new Suggestion("PAYPAL", 4));
        assertThat(CategoryRules.suggest("Deposit from Cash App Jane Doe", history))
                .isEqualTo(new Suggestion("CASH APP", 1));
        // A store after the processor is used as usual.
        assertThat(CategoryRules.suggest("PAYPAL *STEAMGAMES 4029357733", history))
                .isEqualTo(new Suggestion("STEAMGAMES", 1));
        // "Digital Card Purchase" is bank filler, like "Debit Card Purchase".
        assertThat(CategoryRules.suggest("Digital Card Purchase - DEPOP P476922841744 8449353879 NY", history))
                .isEqualTo(new Suggestion("DEPOP", 2));
        // Person-to-person apps: never the linking words.
        assertThat(CategoryRules.suggest("Zelle money received from JOHN ROE", history).pattern())
                .isNotIn("FROM", "MONEY", "RECEIVED");
    }

    private static List<String> normalized(String... descriptions) {
        return List.of(descriptions).stream().map(CategoryRules::normalize).toList();
    }
}
