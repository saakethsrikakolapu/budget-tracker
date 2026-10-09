package com.saaketh.budget.imports.csv;

import com.saaketh.budget.imports.parser.StatementParseException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * A CSV file as a plain grid of trimmed cells, with no assumptions about headers or columns.
 * Blank lines are dropped. Each row remembers its line number in the file for error messages.
 */
public record CsvTable(List<Row> rows) {

    /** @param line 1-based line number in the original file */
    public record Row(int line, List<String> cells) {

        /** The cell at this index, or "" if the row is shorter (banks are inconsistent about trailing commas). */
        public String cell(int index) {
            return index >= 0 && index < cells.size() ? cells.get(index) : "";
        }
    }

    static final int MAX_ROWS = 10_000;

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setIgnoreEmptyLines(true)
            .setTrim(true)
            .get();

    public static CsvTable read(String text) {
        // Some tools add an invisible "byte order mark" at the start of the file.
        String content = text.startsWith("\uFEFF") ? text.substring(1) : text;
        // Where each line starts, to turn a record's character position into a line number.
        List<Integer> lineStarts = new ArrayList<>(List.of(0));
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                lineStarts.add(i + 1);
            }
        }
        List<Row> rows = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse(content, FORMAT)) {
            for (CSVRecord record : parser) {
                List<String> cells = record.toList().stream().map(String::trim).toList();
                if (cells.stream().allMatch(String::isEmpty)) {
                    continue; // a line of only commas
                }
                if (rows.size() >= MAX_ROWS + 50) { // allow a few header/summary lines beyond the limit
                    throw new StatementParseException(
                            "The file has more than %,d rows. Split it into smaller files.".formatted(MAX_ROWS));
                }
                rows.add(new Row(lineOf(lineStarts, record.getCharacterPosition()), cells));
            }
        } catch (IOException | UncheckedIOException e) {
            // e.g. a quote that's opened but never closed
            throw new StatementParseException("The file is not valid CSV.");
        }
        if (rows.isEmpty()) {
            throw new StatementParseException("The file is empty.");
        }
        return new CsvTable(rows);
    }

    /** 1-based line containing this character position (binary search over line starts). */
    private static int lineOf(List<Integer> lineStarts, long position) {
        int index = java.util.Collections.binarySearch(lineStarts, (int) position);
        return index >= 0 ? index + 1 : -index - 1;
    }

    public int columnCount() {
        return rows.stream().mapToInt(r -> r.cells().size()).max().orElse(0);
    }
}
