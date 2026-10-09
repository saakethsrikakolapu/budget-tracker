package com.saaketh.budget.imports.csv;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Recognizing and parsing individual cell values: dates and money. No knowledge of any bank. */
public final class Cells {

    private Cells() {
    }

    /**
     * Date formats US banks use, tried in this order. STRICT resolving rejects impossible dates
     * like 02/30/2026. ("uuuu" is the year in STRICT mode.) Day-before-month formats are left out:
     * a US file with "03/04/2026" means March 4, and guessing otherwise would silently swap dates.
     */
    public static final List<String> DATE_PATTERNS = List.of(
            "uuuu-MM-dd", "M/d/uuuu", "M/d/uu", "uuuu/M/d", "M-d-uuuu", "M-d-uu", "MMM d, uuuu", "d MMM uuuu");

    private static final Pattern MONEY = Pattern.compile("^[+-]?\\(?[+-]?\\$?[0-9][0-9,]*(\\.[0-9]{1,2})?\\)?$");

    public static Optional<LocalDate> parseDate(String value, String pattern) {
        if (value.isEmpty()) {
            return Optional.empty();
        }
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern, java.util.Locale.US)
                    .withResolverStyle(ResolverStyle.STRICT);
            // Some banks add a time ("07/30/2026 00:00:00"); the date is the first part.
            String datePart = pattern.contains(" ") ? value : value.split("[ T]")[0];
            return Optional.of(LocalDate.parse(datePart, formatter));
        } catch (DateTimeParseException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** The first pattern that parses this value, if any. */
    public static Optional<String> dateFormatOf(String value) {
        return DATE_PATTERNS.stream().filter(p -> parseDate(value, p).isPresent()).findFirst();
    }

    /** "$1,234.50", "-4.75", "(12.00)" (accounting style for negative), "+3". */
    public static boolean looksLikeMoney(String value) {
        return !value.isEmpty() && MONEY.matcher(value.replace(" ", "")).matches();
    }

    /** True for values like "4.75" (has cents), as opposed to IDs like "1234" or "0705". */
    public static boolean hasCents(String value) {
        return value.matches(".*\\.[0-9]{2}\\)?$");
    }

    /** Parses an exact decimal; empty -> null. Throws NumberFormatException if it isn't money. */
    public static BigDecimal parseMoney(String value) {
        if (value.isEmpty()) {
            return null;
        }
        String v = value.replace(" ", "");
        if (!MONEY.matcher(v).matches()) {
            throw new NumberFormatException(value);
        }
        boolean parenthesized = v.contains("(") && v.contains(")");
        String digits = v.replaceAll("[()$,+]", "");
        // BigDecimal from a String is exact; new BigDecimal(0.1) from a double would not be.
        BigDecimal amount = new BigDecimal(digits);
        return parenthesized ? amount.abs().negate() : amount;
    }
}
