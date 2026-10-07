package com.saaketh.budget.transaction;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Identifies "the same purchase" across overlapping statement exports, for duplicate detection.
 * Posted date and bank category are left out on purpose: they can change between exports.
 *
 * <p>Must stay identical to the SQL backfill in V3__add_transaction_fingerprints.sql.
 */
public final class TransactionFingerprint {

    private TransactionFingerprint() {
    }

    /** @return 64 hex characters (SHA-256) */
    public static String of(LocalDate transactionDate, BigDecimal amount, String description) {
        String input = transactionDate + "|" + amount.setScale(2).toPlainString() + "|" + normalize(description);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in Java", e);
        }
    }

    /** "  Poshmark  Inc " and "POSHMARK INC" are the same merchant. */
    static String normalize(String description) {
        String collapsed = description.replaceAll("\\s+", " ");
        // Strip only spaces (like SQL btrim), not other characters String.trim() would remove.
        return collapsed.replaceAll("^ | $", "").toUpperCase(Locale.ROOT);
    }
}
