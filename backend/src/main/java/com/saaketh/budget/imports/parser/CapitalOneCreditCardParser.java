package com.saaketh.budget.imports.parser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/**
 * Parses Capital One credit card exports:
 * {@code Transaction Date,Posted Date,Card No.,Description,Category,Debit,Credit}.
 * Dates are yyyy-MM-dd. Each row has either a Debit (money spent) or a Credit (refund/payment).
 */
@Component
public class CapitalOneCreditCardParser implements StatementParser {

    static final int MAX_ROWS = 10_000;
    static final int MAX_REPORTED_ERRORS = 20;

    // Card No. is in the file but deliberately not required or stored: budgeting doesn't need it.
    static final List<String> REQUIRED_COLUMNS =
            List.of("Transaction Date", "Posted Date", "Description", "Category", "Debit", "Credit");

    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final int MAX_CATEGORY_LENGTH = 100;
    /** NUMERIC(12,2) holds at most 10 digits before the decimal point. */
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()                 // read column names from the first line
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)   // exports often end with blank lines
            .setTrim(true)
            .get();

    @Override
    public List<ParsedTransaction> parse(String csvText) {
        // Some tools save CSVs with an invisible "byte order mark" at the start; it would
        // otherwise become part of the first column name.
        String text = csvText.startsWith("﻿") ? csvText.substring(1) : csvText;

        List<ParsedTransaction> transactions = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try (CSVParser parser = CSVParser.parse(text, FORMAT)) {
            checkColumns(parser.getHeaderMap());
            int expectedColumns = parser.getHeaderMap().size();

            for (CSVRecord record : parser) {
                if (record.getRecordNumber() > MAX_ROWS) {
                    throw new StatementParseException(
                            "The file has more than %,d transactions. Split it into smaller files.".formatted(MAX_ROWS));
                }
                // +1 because line 1 is the header (matches what you'd see in a spreadsheet).
                long line = record.getRecordNumber() + 1;
                try {
                    transactions.add(parseRow(record, expectedColumns));
                } catch (InvalidRowException e) {
                    errors.add("Line " + line + ": " + e.getMessage());
                    if (errors.size() >= MAX_REPORTED_ERRORS) {
                        errors.add("Too many errors; stopped checking.");
                        break;
                    }
                }
            }
        } catch (IOException | UncheckedIOException e) {
            // e.g. a quote that's opened but never closed
            throw new StatementParseException("The file is not valid CSV.");
        }

        if (!errors.isEmpty()) {
            throw new StatementParseException(errors);
        }
        if (transactions.isEmpty()) {
            throw new StatementParseException("The file has no transactions.");
        }
        return transactions;
    }

    private static void checkColumns(Map<String, Integer> header) {
        List<String> missing = REQUIRED_COLUMNS.stream().filter(column -> !header.containsKey(column)).toList();
        if (!missing.isEmpty()) {
            throw new StatementParseException("This doesn't look like a Capital One credit card export. Missing columns: "
                    + String.join(", ", missing) + ".");
        }
    }

    private static ParsedTransaction parseRow(CSVRecord record, int expectedColumns) {
        if (record.size() != expectedColumns) {
            throw new InvalidRowException("expected %d columns but found %d".formatted(expectedColumns, record.size()));
        }

        LocalDate transactionDate = parseDate(record.get("Transaction Date"), "Transaction Date");
        if (transactionDate == null) {
            throw new InvalidRowException("Transaction Date is missing");
        }
        LocalDate postedDate = parseDate(record.get("Posted Date"), "Posted Date");

        String description = record.get("Description");
        if (description.isEmpty()) {
            throw new InvalidRowException("Description is missing");
        }
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new InvalidRowException("Description is longer than %d characters".formatted(MAX_DESCRIPTION_LENGTH));
        }

        String category = record.get("Category");
        if (category.length() > MAX_CATEGORY_LENGTH) {
            throw new InvalidRowException("Category is longer than %d characters".formatted(MAX_CATEGORY_LENGTH));
        }

        BigDecimal debit = parseMoney(record.get("Debit"), "Debit");
        BigDecimal credit = parseMoney(record.get("Credit"), "Credit");
        if ((debit == null) == (credit == null)) {
            throw new InvalidRowException("exactly one of Debit or Credit must have a value");
        }
        // Spending is stored as a negative number, money coming back as positive.
        BigDecimal amount = debit != null ? debit.negate() : credit;

        return new ParsedTransaction(transactionDate, postedDate, description, amount,
                category.isEmpty() ? null : category);
    }

    /** Returns null for an empty cell. */
    private static LocalDate parseDate(String value, String column) {
        if (value.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(value); // ISO format: yyyy-MM-dd
        } catch (DateTimeParseException e) {
            throw new InvalidRowException("%s \"%s\" is not a date like 2026-07-30".formatted(column, value));
        }
    }

    /** Returns null for an empty cell. Accepts "1234.5", "1,234.50", "$12.00". */
    private static BigDecimal parseMoney(String value, String column) {
        if (value.isEmpty()) {
            return null;
        }
        BigDecimal amount;
        try {
            // BigDecimal from a String is exact. new BigDecimal(0.1) (from a double) would not be.
            amount = new BigDecimal(value.replace(",", "").replace("$", ""));
        } catch (NumberFormatException e) {
            throw new InvalidRowException("%s \"%s\" is not a number".formatted(column, value));
        }
        if (amount.signum() < 0) {
            throw new InvalidRowException("%s must not be negative".formatted(column));
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new InvalidRowException("%s \"%s\" has more than 2 decimal places".formatted(column, value));
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new InvalidRowException("%s \"%s\" is too large".formatted(column, value));
        }
        return amount.setScale(2);
    }

    /** A problem with one row; collected into the file's error list. */
    private static class InvalidRowException extends RuntimeException {
        InvalidRowException(String message) {
            super(message);
        }
    }
}
