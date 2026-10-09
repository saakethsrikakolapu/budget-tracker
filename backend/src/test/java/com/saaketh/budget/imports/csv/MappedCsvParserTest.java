package com.saaketh.budget.imports.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saaketh.budget.imports.csv.ColumnMapping.AmountStyle;
import com.saaketh.budget.imports.parser.ParsedTransaction;
import com.saaketh.budget.imports.parser.StatementParseException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Plain unit tests for reading rows with a known mapping: no Spring, no database. */
class MappedCsvParserTest {

    private static final String HEADER = "Transaction Date,Posted Date,Card No.,Description,Category,Debit,Credit\n";

    /** Debit/credit layout: date 0, posted 1, description 3, category 4, out 5, in 6. */
    private static final ColumnMapping DEBIT_CREDIT = new ColumnMapping(0, 1, 3, 4, AmountStyle.DEBIT_CREDIT,
            null, 5, 6, null, false, "uuuu-MM-dd");

    @Test
    void moneyOutIsNegativeAndMoneyInIsPositive() {
        List<ParsedTransaction> rows = parse(HEADER + """
                2026-09-28,2026-09-29,1234,CAMPUS COFFEE CO,Dining,4.75,
                2026-09-24,2026-09-24,1234,AUTOPAY PYMT,Payment/Credit,,250.00
                """);

        assertThat(rows.get(0).amount()).isEqualTo(new BigDecimal("-4.75"));
        assertThat(rows.get(1).amount()).isEqualTo(new BigDecimal("250.00"));
        assertThat(rows.get(0).bankCategory()).isEqualTo("Dining");
    }

    @Test
    void amountsAreExactDecimals() {
        List<ParsedTransaction> rows = parse(HEADER + """
                2026-09-01,2026-09-02,1234,A,Other,0.1,
                2026-09-01,2026-09-02,1234,B,Other,0.2,
                2026-09-01,2026-09-02,1234,C,Other,"$1,234.5",
                """);

        // With double, 0.1 + 0.2 = 0.30000000000000004. BigDecimal is exact.
        assertThat(rows.get(0).amount().add(rows.get(1).amount())).isEqualByComparingTo("-0.30");
        assertThat(rows.get(2).amount()).isEqualTo(new BigDecimal("-1234.50"));
    }

    @Test
    void handlesQuotedCommasBlankLinesByteOrderMarkAndTrailingCommas() {
        List<ParsedTransaction> rows = parse("﻿" + HEADER + """
                2026-09-27,2026-09-28,1234,"BOOKS, SUPPLIES & MORE",Merchandise,62.10,,

                2026-09-26,2026-09-27,1234,STREAMFLIX,Entertainment,15.49,


                """);

        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst().description()).isEqualTo("BOOKS, SUPPLIES & MORE");
    }

    @Test
    void creditsWrittenAsNegativeAreStillMoneyIn() {
        // Some banks write refunds as negative numbers in the credit column; the column decides.
        List<ParsedTransaction> rows = parse(HEADER + "2026-09-20,2026-09-21,1234,RETURN,Merchandise,,-23.99\n");

        assertThat(rows.getFirst().amount()).isEqualTo(new BigDecimal("23.99"));
    }

    @Test
    void rowsWithNoAmountAreSkippedNotErrors() {
        CsvTable table = CsvTable.read(HEADER + """
                2026-09-01,,1234,BEGINNING BALANCE,,,
                2026-09-02,2026-09-03,1234,COFFEE,Dining,4.75,
                """);

        MappedCsvParser.Result result = MappedCsvParser.parse(table, DEBIT_CREDIT);

        assertThat(result.transactions()).hasSize(1);
        assertThat(result.skippedRows()).isEqualTo(1);
    }

    @Test
    void reportsEveryBadRowWithItsLineNumber() {
        assertThatThrownBy(() -> parse(HEADER + """
                2026-09-01,2026-09-02,1234,GOOD ROW,Other,1.00,
                09/02/2026,2026-09-03,1234,BAD DATE,Other,1.00,
                2026-09-03,2026-09-04,1234,BOTH AMOUNTS,Other,1.00,2.00
                2026-09-05,2026-09-06,1234,NOT A NUMBER,Other,abc,
                2026-09-07,2026-09-08,1234,TOO PRECISE,Other,1.005,
                2026-09-08,2026-09-09,1234,,Other,1.00,
                """))
                .isInstanceOfSatisfying(StatementParseException.class, e -> assertThat(e.getErrors()).containsExactly(
                        "Row 3: date \"09/02/2026\" isn't a date like 2026-07-30",
                        "Row 4: it has both a money-out and a money-in amount",
                        "Row 5: the money-out \"abc\" isn't a number with at most 2 decimal places",
                        "Row 6: the money-out \"1.005\" isn't a number with at most 2 decimal places",
                        "Row 7: the description is empty"));
    }

    @Test
    void typeColumnDecidesDirectionForUnsignedAmounts() {
        ColumnMapping mapping = new ColumnMapping(0, null, 1, null, AmountStyle.UNSIGNED_WITH_TYPE,
                2, null, null, 3, false, "M/d/uu");
        List<ParsedTransaction> rows = MappedCsvParser.parse(CsvTable.read("""
                10/28/26,COFFEE,4.75,Debit
                10/25/26,PAYROLL,400.00,Credit
                """), mapping).transactions();

        assertThat(rows.get(0).amount()).isEqualByComparingTo("-4.75");
        assertThat(rows.get(1).amount()).isEqualByComparingTo("400.00");
    }

    @Test
    void rejectsMappingsThatDontFitTheFile() {
        ColumnMapping outOfRange = new ColumnMapping(0, null, 9, null, AmountStyle.SIGNED, 2, null, null, null,
                false, "uuuu-MM-dd");

        assertThatThrownBy(() -> MappedCsvParser.parse(CsvTable.read("2026-09-01,COFFEE,-4.75\n"), outOfRange))
                .isInstanceOf(StatementParseException.class)
                .hasMessageContaining("could not be imported");
    }

    @Test
    void rejectsUnclosedQuoteAndEmptyFiles() {
        assertThatThrownBy(() -> CsvTable.read(HEADER + "2026-09-01,2026-09-02,1234,\"BROKEN,Other,1.00,\n"))
                .isInstanceOfSatisfying(StatementParseException.class,
                        e -> assertThat(e.getErrors()).containsExactly("The file is not valid CSV."));
        assertThatThrownBy(() -> CsvTable.read("\n\n,,,\n")).isInstanceOf(StatementParseException.class);
        assertThatThrownBy(() -> parse(HEADER)).isInstanceOf(StatementParseException.class);
    }

    @Test
    void stopsAfterTooManyErrors() {
        String good = "2026-09-01,2026-09-02,1234,OK,Other,1.00,\n";
        String bad = "2026-09-01,2026-09-02,1234,X,Other,abc,\n";

        assertThatThrownBy(() -> parse(HEADER + good + bad.repeat(50)))
                .isInstanceOfSatisfying(StatementParseException.class, e -> {
                    assertThat(e.getErrors()).hasSize(MappedCsvParser.MAX_REPORTED_ERRORS + 1);
                    assertThat(e.getErrors().getLast()).isEqualTo("Too many errors; stopped checking.");
                });
    }

    @Test
    void rejectsMoreThanMaxTransactions() {
        String rows = "2026-09-01,2026-09-02,1234,X,Other,1.00,\n".repeat(MappedCsvParser.MAX_TRANSACTIONS + 1);

        assertThatThrownBy(() -> parse(HEADER + rows))
                .isInstanceOfSatisfying(StatementParseException.class,
                        e -> assertThat(e.getErrors().getFirst()).contains("more than 10,000"));
    }

    private static List<ParsedTransaction> parse(String csv) {
        return MappedCsvParser.parse(CsvTable.read(csv), DEBIT_CREDIT).transactions();
    }
}
