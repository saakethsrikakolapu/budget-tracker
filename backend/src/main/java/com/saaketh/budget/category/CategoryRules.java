package com.saaketh.budget.category;

import com.saaketh.budget.recurring.MerchantKey;
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

    /**
     * @param pattern the text an "always put this merchant in ..." rule should match
     * @param matchCount how many of the user's transactions (including this one) it matches
     */
    public record Suggestion(String pattern, long matchCount) {
    }

    /**
     * The merchant part of a description, chosen so one rule catches every way the bank writes it:
     * "PURCHASE AUTHORIZED ON 10/21 STREAMFLIX CARD 1234", "Debit Card Purchase - STREAMFLIX LOS
     * GATOS CA" and "DEBIT CARD PURCHASE STREAMFLIX" all become "STREAMFLIX".
     *
     * <p>Candidates are the leading merchant words ("STREAMFLIX", "STREAMFLIX LOS", ...). The one
     * matching the most of the user's transactions wins; ties go to the longer, more specific one
     * ("HARRIS TEETER" over "HARRIS" when both match the same transactions).
     *
     * @param normalizedDescriptions the user's descriptions, already {@link #normalize}d
     */
    public static Suggestion suggest(String description, List<String> normalizedDescriptions) {
        String normalized = normalize(description);
        List<String> words = MerchantKey.words(description);
        Suggestion best = null;
        for (int k = 1; k <= words.size(); k++) {
            String candidate = String.join(" ", words.subList(0, k));
            // Words come from text with punctuation removed ("STREAMFLIX.COM" -> "STREAMFLIX COM"), so
            // a candidate must still appear in the description as the bank wrote it.
            if (candidate.length() < 2 || !normalized.contains(candidate)) {
                continue;
            }
            long count = normalizedDescriptions.stream().filter(d -> d.contains(candidate)).count();
            if (best == null || count >= best.matchCount()) {
                best = new Suggestion(candidate, count);
            }
        }
        if (best != null) {
            return best;
        }
        // No usable merchant words (e.g. all numbers): fall back to the description itself.
        String fallback = normalized.length() > 100 ? normalized.substring(0, 100).trim() : normalized;
        return new Suggestion(fallback, normalizedDescriptions.stream().filter(d -> d.contains(fallback)).count());
    }
}
