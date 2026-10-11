package com.saaketh.budget.category;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Pure matching logic for the built-in merchant dictionary (no database), so it's easy to test.
 * Must stay identical to the SQL backfill in V11__create_merchant_keywords.sql.
 */
public final class MerchantKeywords {

    private MerchantKeywords() {
    }

    /** One dictionary entry. keyword is already normalized (see {@link #normalize}). */
    public record Keyword(String keyword, String categoryName, boolean moneyInOnly) {
    }

    /** Longest keyword first (most specific wins); ties broken alphabetically so results are stable. */
    static final Comparator<Keyword> MOST_SPECIFIC_FIRST =
            Comparator.comparingInt((Keyword k) -> k.keyword().length()).reversed()
                    .thenComparing(Keyword::keyword);

    /**
     * Uppercase words of letters and digits separated by single spaces:
     * "SQ *JOE'S PIZZA #12" -> "SQ JOE S PIZZA 12", "APPLE.COM/BILL" -> "APPLE COM BILL".
     */
    public static String normalize(String text) {
        return text.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").trim();
    }

    /**
     * The most specific keyword that appears as whole words in the description. Income-style
     * keywords (moneyInOnly) only count for money coming in.
     *
     * @param keywordsMostSpecificFirst sorted with {@link #MOST_SPECIFIC_FIRST}
     */
    static Optional<Keyword> match(List<Keyword> keywordsMostSpecificFirst, String description, BigDecimal amount) {
        // Padding with spaces makes " PIZZA " match only the whole word PIZZA.
        String padded = " " + normalize(description) + " ";
        boolean moneyIn = amount != null && amount.signum() > 0;
        return keywordsMostSpecificFirst.stream()
                .filter(k -> !k.moneyInOnly() || moneyIn)
                .filter(k -> padded.contains(" " + k.keyword() + " "))
                .findFirst();
    }
}
