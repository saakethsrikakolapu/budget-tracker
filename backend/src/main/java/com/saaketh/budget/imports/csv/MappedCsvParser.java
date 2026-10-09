package com.saaketh.budget.imports.csv;

import com.saaketh.budget.imports.csv.CsvTable.Row;
import com.saaketh.budget.imports.parser.ParsedTransaction;
import com.saaketh.budget.imports.parser.StatementParseException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Turns rows into transactions using a {@link ColumnMapping}. All-or-nothing: any invalid row
 * means nothing is returned, with every problem listed ("Row 7: ..."), up to a limit.
 */
public final class MappedCsvParser {

    /**
     * @param skippedRows data rows with no amount at all (e.g. "Beginning balance" lines), left out on purpose
     */
    public record Result(List<ParsedTransaction> transactions, int skippedRows) {
    }

    static final int MAX_TRANSACTIONS = 10_000;
    static final int MAX_REPORTED_ERRORS = 20;
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final int MAX_CATEGORY_LENGTH = 100;
    /** NUMERIC(12,2) holds at most 10 digits before the decimal point. */
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");

    private MappedCsvParser() {
    }

    public static Result parse(CsvTable table, ColumnMapping mapping) {
        validate(mapping, table.columnCount());
        List<Row> rows = table.rows();

        // Everything before the first row with a valid date is a header or summary text.
        int start = 0;
        while (start < rows.size() && date(rows.get(start), mapping.dateColumn(), mapping).isEmpty()) {
            start++;
        }
        if (start == rows.size()) {
            throw new StatementParseException("No row has a date in the date column (%s)."
                    .formatted(example(mapping.dateFormat())));
        }

        List<ParsedTransaction> transactions = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int skipped = 0;
        for (int i = start; i < rows.size(); i++) {
            Row row = rows.get(i);
            try {
                Optional<ParsedTransaction> parsed = parseRow(row, mapping);
                if (parsed.isPresent()) {
                    transactions.add(parsed.get());
                    if (transactions.size() > MAX_TRANSACTIONS) {
                        throw new StatementParseException(
                                "The file has more than %,d transactions. Split it into smaller files.".formatted(MAX_TRANSACTIONS));
                    }
                } else {
                    skipped++;
                }
            } catch (InvalidRowException e) {
                errors.add("Row " + row.line() + ": " + e.getMessage());
                if (errors.size() >= MAX_REPORTED_ERRORS) {
                    errors.add("Too many errors; stopped checking.");
                    break;
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new StatementParseException(errors);
        }
        if (transactions.isEmpty()) {
            throw new StatementParseException("The file has no transactions.");
        }
        return new Result(transactions, skipped);
    }

    /** Empty Optional = a row with no amount, which is skipped rather than treated as an error. */
    private static Optional<ParsedTransaction> parseRow(Row row, ColumnMapping m) {
        LocalDate transactionDate = date(row, m.dateColumn(), m).orElseThrow(() -> new InvalidRowException(
                "date \"%s\" isn't a date like %s".formatted(row.cell(m.dateColumn()), example(m.dateFormat()))));
        // Posted dates are optional; pending transactions often have none.
        LocalDate postedDate = m.postedDateColumn() == null ? null : date(row, m.postedDateColumn(), m).orElse(null);

        Optional<BigDecimal> amount = amount(row, m);
        if (amount.isEmpty()) {
            return Optional.empty();
        }

        String description = row.cell(m.descriptionColumn()).replaceAll("\\s+", " ").trim();
        if (description.isEmpty()) {
            throw new InvalidRowException("the description is empty");
        }
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new InvalidRowException("the description is longer than %d characters".formatted(MAX_DESCRIPTION_LENGTH));
        }
        String category = m.categoryColumn() == null ? "" : row.cell(m.categoryColumn());
        if (category.length() > MAX_CATEGORY_LENGTH) {
            throw new InvalidRowException("the category is longer than %d characters".formatted(MAX_CATEGORY_LENGTH));
        }
        return Optional.of(new ParsedTransaction(transactionDate, postedDate, description, amount.get(),
                category.isEmpty() ? null : category));
    }

    /** Negative = money spent, positive = money in. Empty = the row has no amount. */
    private static Optional<BigDecimal> amount(Row row, ColumnMapping m) {
        BigDecimal result;
        switch (m.amountStyle()) {
            case SIGNED -> {
                BigDecimal v = money(row, m.amountColumn(), "amount");
                if (v == null) {
                    return Optional.empty();
                }
                result = m.positiveIsSpending() ? v.negate() : v;
            }
            case DEBIT_CREDIT -> {
                BigDecimal out = nonZeroOrNull(money(row, m.debitColumn(), "money-out"));
                BigDecimal in = nonZeroOrNull(money(row, m.creditColumn(), "money-in"));
                if (out == null && in == null) {
                    return Optional.empty();
                }
                if (out != null && in != null) {
                    throw new InvalidRowException("it has both a money-out and a money-in amount");
                }
                // Banks disagree on signs inside these columns (some write refunds as negative
                // credits), so only the column decides the direction.
                result = out != null ? out.abs().negate() : in.abs();
            }
            case UNSIGNED_WITH_TYPE -> {
                BigDecimal v = money(row, m.amountColumn(), "amount");
                if (v == null) {
                    return Optional.empty();
                }
                String type = row.cell(m.typeColumn()).toLowerCase(Locale.ROOT);
                if (containsAny(type, "debit", "withdrawal", "purchase", "sale", "charge", "fee")) {
                    result = v.abs().negate();
                } else if (containsAny(type, "credit", "deposit", "refund", "return")) {
                    result = v.abs();
                } else {
                    throw new InvalidRowException("couldn't tell whether \"%s\" is money in or out".formatted(row.cell(m.typeColumn())));
                }
            }
            default -> throw new IllegalStateException();
        }
        if (result.abs().compareTo(MAX_AMOUNT) > 0) {
            throw new InvalidRowException("the amount %s is too large".formatted(result.abs().toPlainString()));
        }
        return Optional.of(result.setScale(2));
    }

    private static BigDecimal money(Row row, Integer column, String what) {
        String value = row.cell(column);
        try {
            return Cells.parseMoney(value);
        } catch (NumberFormatException e) {
            throw new InvalidRowException("the %s \"%s\" isn't a number with at most 2 decimal places".formatted(what, value));
        }
    }

    private static BigDecimal nonZeroOrNull(BigDecimal v) {
        return v == null || v.signum() == 0 ? null : v;
    }

    private static Optional<LocalDate> date(Row row, int column, ColumnMapping m) {
        return Cells.parseDate(row.cell(column), m.dateFormat());
    }

    private static boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /** "M/d/uuuu" -> "7/30/2026", for error messages people can understand. */
    static String example(String pattern) {
        return LocalDate.of(2026, 7, 30).format(
                java.time.format.DateTimeFormatter.ofPattern(pattern, Locale.US));
    }

    /** Rejects mappings that point at columns the file doesn't have (e.g. edited by hand). */
    private static void validate(ColumnMapping m, int columns) {
        if (m.amountStyle() == null || m.dateFormat() == null) {
            throw new StatementParseException("The column choices are incomplete. Choose the columns again.");
        }
        List<Integer> required = new ArrayList<>(List.of(m.dateColumn(), m.descriptionColumn()));
        switch (m.amountStyle()) {
            case SIGNED -> required.add(m.amountColumn());
            case DEBIT_CREDIT -> {
                required.add(m.debitColumn());
                required.add(m.creditColumn());
            }
            case UNSIGNED_WITH_TYPE -> {
                required.add(m.amountColumn());
                required.add(m.typeColumn());
            }
        }
        for (Integer column : required) {
            if (column == null || column < 0 || column >= columns) {
                throw new StatementParseException("The column choices don't match this file. Choose the columns again.");
            }
        }
        if (!Cells.DATE_PATTERNS.contains(m.dateFormat())) {
            throw new StatementParseException("Unknown date format.");
        }
    }

    private static class InvalidRowException extends RuntimeException {
        InvalidRowException(String message) {
            super(message);
        }
    }
}
