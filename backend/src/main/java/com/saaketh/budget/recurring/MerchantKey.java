package com.saaketh.budget.recurring;

import com.saaketh.budget.category.MerchantKeywords;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reduces a bank description to a stable merchant key, so the same merchant groups together
 * across months even when the description carries dates, store numbers, or card numbers:
 * "PURCHASE AUTHORIZED ON 10/27 NETFLIX.COM CARD 1234" -> "NETFLIX COM".
 */
public final class MerchantKey {

    private MerchantKey() {
    }

    static final int MAX_WORDS = 4;

    /** Filler words banks wrap around the merchant name. */
    private static final Set<String> FILLER = Set.of(
            "PURCHASE", "AUTHORIZED", "ON", "CARD", "DEBIT", "CREDIT", "POS", "ACH", "RECURRING", "CHECKCARD",
            "WEB", "ID", "PPD", "WWW", "SQ", "TST", "PAYPAL", "INC", "LLC", "CO", "THE", "US", "USA", "VISA",
            "DDA", "TRANSACTION", "PREAUTHORIZED", "ONLINE", "PMT", "PAYMENT", "PAYMENTS", "BILL", "BILLPAY", "ELECTRONIC",
            "WITHDRAWAL");

    public static String of(String description) {
        List<String> words = Arrays.stream(MerchantKeywords.normalize(description).split(" "))
                .filter(w -> !w.isEmpty())
                .filter(w -> w.chars().noneMatch(Character::isDigit)) // dates, store and card numbers
                .filter(w -> !FILLER.contains(w))
                .filter(w -> w.length() > 1) // stray letters left over from symbols
                .limit(MAX_WORDS)
                .toList();
        return words.isEmpty() ? MerchantKeywords.normalize(description) : words.stream().collect(Collectors.joining(" "));
    }
}
