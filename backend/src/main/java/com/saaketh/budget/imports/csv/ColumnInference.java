package com.saaketh.budget.imports.csv;

import com.saaketh.budget.imports.csv.ColumnMapping.AmountStyle;
import com.saaketh.budget.imports.csv.CsvTable.Row;
import com.saaketh.budget.imports.parser.StatementParseException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Figures out which column is which by looking at the VALUES, not the header names, so it works
 * for any bank, including files with no header row. Header names (when present) only break ties.
 *
 * <p>How each role is recognized:
 * <ul>
 *   <li>date: nearly every value parses as a date; with two date columns, the earlier one is the
 *       transaction date (a charge posts on or after the day it happened)</li>
 *   <li>amount: values look like money with cents; two money columns where each row fills exactly
 *       one are debit/credit; a column that changes by exactly the amount each row is a running
 *       balance and is ignored</li>
 *   <li>sign: most rows in any statement are purchases, so the majority sign means "spending"</li>
 *   <li>description: the text column with the most variety (merchant names rarely repeat exactly)</li>
 * </ul>
 */
public final class ColumnInference {

    /**
     * @param headerRow index into the table's rows of the header line, or -1 if the file has none
     * @param columnNames header text per column, or "Column 1", "Column 2", ... without a header
     * @param warnings things the user should double-check in the preview
     */
    public record Result(ColumnMapping mapping, int headerRow, List<String> columnNames, List<String> warnings) {
    }

    /** "Nearly every value": tolerates a stray pending row or footer line. */
    static final double MOSTLY = 0.9;

    private static final Pattern DIGITS_ONLY = Pattern.compile("^[0-9*xX#-]+$");
    private static final Set<String> SPENDING_WORDS = Set.of("debit", "withdrawal", "purchase", "sale", "charge", "fee", "dr");
    private static final Set<String> INCOME_WORDS = Set.of("credit", "deposit", "refund", "return", "cr");

    private ColumnInference() {
    }

    public static Result infer(CsvTable table) {
        List<Row> rows = table.rows();
        int dataStart = findDataStart(rows);
        if (dataStart < 0) {
            throw new StatementParseException(
                    "Couldn't find any transactions: no rows have both a date and an amount.");
        }
        int headerRow = dataStart > 0 && looksLikeHeader(rows.get(dataStart - 1)) ? dataStart - 1 : -1;
        List<Row> data = rows.subList(dataStart, rows.size());
        int columns = Math.max(table.columnCount(), 1);

        List<String> names = new ArrayList<>();
        for (int c = 0; c < columns; c++) {
            String header = headerRow >= 0 ? rows.get(headerRow).cell(c) : "";
            names.add(header.isEmpty() ? "Column " + (c + 1) : header);
        }
        List<Stats> stats = new ArrayList<>();
        for (int c = 0; c < columns; c++) {
            stats.add(Stats.of(data, c, headerRow >= 0 ? names.get(c).toLowerCase(Locale.ROOT) : ""));
        }
        List<String> warnings = new ArrayList<>();

        // ---- Dates ----
        List<Stats> dateColumns = stats.stream().filter(Stats::isDateColumn).toList();
        if (dateColumns.isEmpty()) {
            throw new StatementParseException("Couldn't find a date column.");
        }
        Stats txDate = dateColumns.getFirst();
        Stats posted = null;
        if (dateColumns.size() >= 2) {
            Stats a = dateColumns.get(0);
            Stats b = dateColumns.get(1);
            boolean aNamedPosted = a.nameMatches("post", "clear", "settle");
            boolean bNamedPosted = b.nameMatches("post", "clear", "settle");
            if (aNamedPosted != bNamedPosted) {
                txDate = aNamedPosted ? b : a;
            } else {
                txDate = earlierMostOfTheTime(data, a, b) ? a : b;
            }
            posted = txDate == a ? b : a;
        }
        String dateFormat = txDate.dateFormat;

        // ---- Money ----
        Set<Integer> used = new HashSet<>();
        used.add(txDate.index);
        if (posted != null) {
            used.add(posted.index);
        }
        List<Stats> money = stats.stream().filter(s -> !used.contains(s.index) && s.isMoneyColumn()).toList();
        AmountStyle style;
        Integer amount = null;
        Integer debit = null;
        Integer credit = null;
        Integer type = null;
        boolean positiveIsSpending = false;

        Optional<Stats[]> pair = debitCreditPair(data, money);
        if (pair.isPresent()) {
            style = AmountStyle.DEBIT_CREDIT;
            debit = pair.get()[0].index;
            credit = pair.get()[1].index;
            used.add(debit);
            used.add(credit);
        } else {
            // At least half filled: some rows (like "Beginning balance" lines) have no amount.
            List<Stats> candidates = new ArrayList<>(money.stream().filter(s -> s.filledFraction >= 0.5).toList());
            if (candidates.isEmpty()) {
                throw new StatementParseException("Couldn't find an amount column.");
            }
            removeRunningBalance(data, candidates);
            Stats chosen = candidates.stream().filter(s -> s.nameMatches("amount")).findFirst()
                    .orElse(candidates.stream().max(Comparator.comparingDouble(s -> s.filledFraction)).orElseThrow());
            amount = chosen.index;
            used.add(amount);
            for (Stats s : stats) {
                if (!used.contains(s.index) && s.isTypeColumn()) {
                    type = s.index;
                    break;
                }
            }
            if (chosen.negatives == 0 && type != null) {
                style = AmountStyle.UNSIGNED_WITH_TYPE;
                used.add(type);
            } else {
                style = AmountStyle.SIGNED;
                type = null;
                // Most rows in a statement are purchases, so the more common sign means spending.
                positiveIsSpending = chosen.positives > chosen.negatives;
                if (chosen.negatives == 0) {
                    warnings.add("Every amount is positive, so they were all treated as spending. "
                            + "If some are money coming in, choose the right columns in the preview.");
                } else if (chosen.positives == chosen.negatives) {
                    warnings.add("Couldn't tell whether purchases are positive or negative. Check the preview.");
                }
            }
        }

        // ---- Description: the text column with the most variety ----
        List<Stats> text = stats.stream()
                .filter(s -> !used.contains(s.index) && s.isTextColumn())
                .sorted(Comparator.comparingDouble(Stats::descriptionScore).reversed())
                .toList();
        if (text.isEmpty()) {
            throw new StatementParseException("Couldn't find a description column.");
        }
        Stats description = text.getFirst();
        if (text.size() > 1 && text.get(1).descriptionScore() > 0.9 * description.descriptionScore()) {
            warnings.add("Two columns look like descriptions (%s and %s). Check the preview."
                    .formatted(names.get(description.index), names.get(text.get(1).index)));
        }
        used.add(description.index);

        // ---- The bank's own category label: only when a header names it (otherwise unguessable) ----
        Integer category = stats.stream()
                .filter(s -> !used.contains(s.index) && s.nameMatches("category") && s.filledFraction > 0)
                .map(s -> s.index)
                .findFirst().orElse(null);

        ColumnMapping mapping = new ColumnMapping(txDate.index, posted == null ? null : posted.index,
                description.index, category, style, amount, debit, credit, type, positiveIsSpending, dateFormat);
        return new Result(mapping, headerRow, names, warnings);
    }

    /** Where the header (if any) is and what each column is called, without deciding roles. */
    public record Layout(int headerRow, List<String> columnNames) {
    }

    public static Layout layout(CsvTable table) {
        List<Row> rows = table.rows();
        int dataStart = findDataStart(rows);
        int headerRow = dataStart > 0 && looksLikeHeader(rows.get(dataStart - 1)) ? dataStart - 1 : -1;
        List<String> names = new ArrayList<>();
        for (int c = 0; c < Math.max(table.columnCount(), 1); c++) {
            String header = headerRow >= 0 ? rows.get(headerRow).cell(c) : "";
            names.add(header.isEmpty() ? "Column " + (c + 1) : header);
        }
        return new Layout(headerRow, names);
    }

    /** First row that has a date and a money value, followed by another such row (or the end). */
    static int findDataStart(List<Row> rows) {
        for (int i = 0; i < rows.size(); i++) {
            if (looksLikeData(rows.get(i)) && (i + 1 == rows.size() || looksLikeData(rows.get(i + 1)))) {
                return i;
            }
        }
        // A file with a single transaction line.
        for (int i = 0; i < rows.size(); i++) {
            if (looksLikeData(rows.get(i))) {
                return i;
            }
        }
        return -1;
    }

    static boolean looksLikeData(Row row) {
        boolean date = row.cells().stream().anyMatch(c -> Cells.dateFormatOf(c).isPresent());
        boolean amount = row.cells().stream().anyMatch(c -> Cells.looksLikeMoney(c) && Cells.hasCents(c));
        return date && amount;
    }

    /** A header has text in at least two cells and no dates or amounts. */
    static boolean looksLikeHeader(Row row) {
        long filled = row.cells().stream().filter(c -> !c.isEmpty()).count();
        boolean anyDateOrMoney = row.cells().stream()
                .anyMatch(c -> Cells.dateFormatOf(c).isPresent() || (Cells.looksLikeMoney(c) && Cells.hasCents(c)));
        return filled >= 2 && !anyDateOrMoney;
    }

    private static boolean earlierMostOfTheTime(List<Row> data, Stats a, Stats b) {
        int aFirst = 0;
        int bFirst = 0;
        for (Row row : data) {
            Optional<LocalDate> da = Cells.parseDate(row.cell(a.index), a.dateFormat);
            Optional<LocalDate> db = Cells.parseDate(row.cell(b.index), b.dateFormat);
            if (da.isPresent() && db.isPresent()) {
                if (da.get().isBefore(db.get())) {
                    aFirst++;
                } else if (db.get().isBefore(da.get())) {
                    bFirst++;
                }
            }
        }
        return aFirst >= bFirst;
    }

    /**
     * Two money columns where (nearly) every row fills exactly one. Returns [money out, money in].
     * Header names decide which is which when present; otherwise the busier column is money out,
     * because most rows in a statement are purchases.
     */
    private static Optional<Stats[]> debitCreditPair(List<Row> data, List<Stats> money) {
        for (int i = 0; i < money.size(); i++) {
            for (int j = i + 1; j < money.size(); j++) {
                Stats a = money.get(i);
                Stats b = money.get(j);
                int exclusive = 0;
                for (Row row : data) {
                    if (nonZero(row.cell(a.index)) != nonZero(row.cell(b.index))) {
                        exclusive++;
                    }
                }
                if (exclusive >= MOSTLY * data.size() && a.filled > 0 && b.filled > 0) {
                    if (a.nameMatches("credit", "deposit", "refund") || b.nameMatches("debit", "withdraw", "charge")) {
                        return Optional.of(new Stats[] {b, a});
                    }
                    if (a.nameMatches("debit", "withdraw", "charge") || b.nameMatches("credit", "deposit", "refund")) {
                        return Optional.of(new Stats[] {a, b});
                    }
                    return Optional.of(a.filled >= b.filled ? new Stats[] {a, b} : new Stats[] {b, a});
                }
            }
        }
        return Optional.empty();
    }

    /** A running balance changes by exactly one row's amount from row to row. */
    private static void removeRunningBalance(List<Row> data, List<Stats> full) {
        if (full.size() < 2) {
            return;
        }
        full.removeIf(s -> s.nameMatches("balance"));
        for (Stats balance : List.copyOf(full)) {
            for (Stats amount : full) {
                if (amount != balance && isRunningBalance(data, balance.index, amount.index)) {
                    full.remove(balance);
                    return;
                }
            }
        }
    }

    private static boolean isRunningBalance(List<Row> data, int balanceCol, int amountCol) {
        int checked = 0;
        int matches = 0;
        for (int i = 1; i < data.size(); i++) {
            try {
                BigDecimal previous = Cells.parseMoney(data.get(i - 1).cell(balanceCol));
                BigDecimal current = Cells.parseMoney(data.get(i).cell(balanceCol));
                BigDecimal amountHere = Cells.parseMoney(data.get(i).cell(amountCol));
                BigDecimal amountBefore = Cells.parseMoney(data.get(i - 1).cell(amountCol));
                if (previous == null || current == null || amountHere == null || amountBefore == null) {
                    continue;
                }
                checked++;
                BigDecimal change = current.subtract(previous).abs();
                // Newest-first files change by the previous row's amount; oldest-first by this row's.
                if (change.compareTo(amountHere.abs()) == 0 || change.compareTo(amountBefore.abs()) == 0) {
                    matches++;
                }
            } catch (NumberFormatException e) {
                // not numeric here; skip this pair
            }
        }
        return checked >= 2 && matches >= 0.8 * checked;
    }

    private static boolean nonZero(String value) {
        if (value.isEmpty()) {
            return false;
        }
        try {
            BigDecimal v = Cells.parseMoney(value);
            return v != null && v.signum() != 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Everything we measure about one column's values. */
    private static final class Stats {
        final int index;
        final String name; // lowercase header text, "" without a header
        int filled;
        double filledFraction;
        double dateFraction;
        String dateFormat;
        double moneyFraction;
        double centsFraction;
        double digitsOnlyFraction;
        double typeWordFraction;
        int distinct;
        double averageLength;
        int positives;
        int negatives;

        private Stats(int index, String name) {
            this.index = index;
            this.name = name;
        }

        static Stats of(List<Row> data, int column, String name) {
            Stats s = new Stats(column, name);
            List<String> values = data.stream().map(r -> r.cell(column)).filter(v -> !v.isEmpty()).toList();
            s.filled = values.size();
            s.filledFraction = data.isEmpty() ? 0 : (double) values.size() / data.size();
            if (values.isEmpty()) {
                return s;
            }
            // The date format that parses the most values in this column.
            int bestDates = 0;
            for (String pattern : Cells.DATE_PATTERNS) {
                int parsed = (int) values.stream().filter(v -> Cells.parseDate(v, pattern).isPresent()).count();
                if (parsed > bestDates) {
                    bestDates = parsed;
                    s.dateFormat = pattern;
                }
            }
            s.dateFraction = (double) bestDates / values.size();
            int money = 0;
            int cents = 0;
            for (String v : values) {
                if (Cells.looksLikeMoney(v)) {
                    money++;
                    if (Cells.hasCents(v)) {
                        cents++;
                    }
                    BigDecimal amount = Cells.parseMoney(v);
                    if (amount.signum() > 0) {
                        s.positives++;
                    } else if (amount.signum() < 0) {
                        s.negatives++;
                    }
                }
            }
            s.moneyFraction = (double) money / values.size();
            s.centsFraction = (double) cents / values.size();
            s.digitsOnlyFraction = (double) values.stream().filter(v -> DIGITS_ONLY.matcher(v).matches()).count()
                    / values.size();
            s.typeWordFraction = (double) values.stream()
                    .map(v -> v.toLowerCase(Locale.ROOT))
                    .filter(v -> SPENDING_WORDS.contains(v) || INCOME_WORDS.contains(v))
                    .count() / values.size();
            s.distinct = (int) values.stream().map(v -> v.toLowerCase(Locale.ROOT)).distinct().count();
            s.averageLength = values.stream().mapToInt(String::length).average().orElse(0);
            return s;
        }

        boolean isDateColumn() {
            return filledFraction >= 0.5 && dateFraction >= MOSTLY && dateFormat != null;
        }

        /** Money with cents (so IDs like "1234" or card numbers aren't mistaken for amounts). */
        boolean isMoneyColumn() {
            return filled > 0 && moneyFraction >= MOSTLY && dateFraction < MOSTLY
                    && (centsFraction >= 0.5 || nameMatches("amount", "debit", "credit", "withdraw", "deposit"));
        }

        boolean isTypeColumn() {
            return filledFraction >= MOSTLY && typeWordFraction >= MOSTLY;
        }

        /**
         * Real text: not dates, not numbers, not codes, and not one value repeated on every row
         * (like a "*" or an account name). With fewer than 3 rows "all the same" proves nothing.
         */
        boolean isTextColumn() {
            return filledFraction >= 0.5 && dateFraction < 0.5 && moneyFraction < 0.5
                    && digitsOnlyFraction < 0.5 && (distinct > 1 || filled < 3);
        }

        /** Merchant names are varied and fairly long; a named header gets a boost as a tie-breaker. */
        double descriptionScore() {
            double variety = (double) distinct / filled;
            // Length only separates real text from short codes ("Debit", "Cleared"); beyond that,
            // longer isn't better (a long "Extended Details" column isn't a better merchant name).
            double score = variety * Math.min(averageLength, 20) * filledFraction;
            // Header names only break ties: an exact common name counts more than a partial match.
            if (Set.of("description", "transaction description", "payee", "merchant", "merchant name", "name")
                    .contains(name.trim())) {
                score *= 2.0;
            } else if (nameMatches("description", "payee", "merchant", "details", "narrative")) {
                score *= 1.2;
            }
            if (nameMatches("category", "type", "memo", "address", "city", "state", "zip", "status", "member")) {
                score *= 0.5;
            }
            return score;
        }

        boolean nameMatches(String... words) {
            for (String word : words) {
                if (name.contains(word)) {
                    return true;
                }
            }
            return false;
        }
    }
}
