package com.saaketh.budget.imports.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Plain unit tests: no Spring, no database. */
class CapitalOneCreditCardParserTest {

    private static final String HEADER = "Transaction Date,Posted Date,Card No.,Description,Category,Debit,Credit\n";

    private final CapitalOneCreditCardParser parser = new CapitalOneCreditCardParser();

    @Test
    void debitBecomesNegativeAndCreditBecomesPositive() {
        List<ParsedTransaction> rows = parser.parse(HEADER + """
                2026-09-28,2026-09-29,1234,CAMPUS COFFEE CO,Dining,4.75,
                2026-09-24,2026-09-24,1234,CAPITAL ONE AUTOPAY PYMT,Payment/Credit,,250.00
                """);

        assertThat(rows).containsExactly(
                new ParsedTransaction(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 29),
                        "CAMPUS COFFEE CO", new BigDecimal("-4.75"), "Dining"),
                new ParsedTransaction(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 24),
                        "CAPITAL ONE AUTOPAY PYMT", new BigDecimal("250.00"), "Payment/Credit"));
    }

    @Test
    void amountsAreExactDecimalsWithTwoPlaces() {
        List<ParsedTransaction> rows = parser.parse(HEADER + """
                2026-09-01,2026-09-02,1234,A,Other,0.1,
                2026-09-01,2026-09-02,1234,B,Other,0.2,
                2026-09-01,2026-09-02,1234,C,Other,"1,234.5",
                """);

        // With double, 0.1 + 0.2 = 0.30000000000000004. BigDecimal is exact.
        BigDecimal sum = rows.get(0).amount().add(rows.get(1).amount());
        assertThat(sum).isEqualByComparingTo("-0.30");
        assertThat(rows.get(2).amount()).isEqualTo(new BigDecimal("-1234.50"));
    }

    @Test
    void handlesQuotedCommasBlankLinesAndByteOrderMark() {
        List<ParsedTransaction> rows = parser.parse("﻿" + HEADER + """
                2026-09-27,2026-09-28,1234,"BOOKS, SUPPLIES & MORE",Merchandise,62.10,

                2026-09-26,2026-09-27,1234,STREAMFLIX,Entertainment,15.49,


                """);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).description()).isEqualTo("BOOKS, SUPPLIES & MORE");
    }

    @Test
    void keepsIdenticalLookingRowsBecauseTheyCanBeRealPurchases() {
        List<ParsedTransaction> rows = parser.parse(HEADER + """
                2026-09-22,2026-09-23,1234,CAMPUS COFFEE CO,Dining,4.75,
                2026-09-22,2026-09-23,1234,CAMPUS COFFEE CO,Dining,4.75,
                """);

        assertThat(rows).hasSize(2);
    }

    @Test
    void emptyPostedDateAndCategoryBecomeNull() {
        ParsedTransaction row = parser.parse(HEADER + "2026-09-01,,1234,PENDING THING,,9.99,\n").getFirst();

        assertThat(row.postedDate()).isNull();
        assertThat(row.bankCategory()).isNull();
    }

    @Test
    void reportsEveryBadRowWithItsLineNumber() {
        assertThatThrownBy(() -> parser.parse(HEADER + """
                2026-09-01,2026-09-02,1234,GOOD ROW,Other,1.00,
                09/02/2026,2026-09-03,1234,BAD DATE,Other,1.00,
                2026-09-03,2026-09-04,1234,BOTH AMOUNTS,Other,1.00,2.00
                2026-09-04,2026-09-05,1234,NO AMOUNT,Other,,
                2026-09-05,2026-09-06,1234,NOT A NUMBER,Other,abc,
                2026-09-06,2026-09-07,1234,NEGATIVE,Other,-5.00,
                2026-09-07,2026-09-08,1234,TOO PRECISE,Other,1.005,
                2026-09-08,2026-09-09,1234,,Other,1.00,
                2026-09-09,2026-09-10,1234,SHORT ROW
                """))
                .isInstanceOfSatisfying(StatementParseException.class, e -> assertThat(e.getErrors()).containsExactly(
                        "Line 3: Transaction Date \"09/02/2026\" is not a date like 2026-07-30",
                        "Line 4: exactly one of Debit or Credit must have a value",
                        "Line 5: exactly one of Debit or Credit must have a value",
                        "Line 6: Debit \"abc\" is not a number",
                        "Line 7: Debit must not be negative",
                        "Line 8: Debit \"1.005\" has more than 2 decimal places",
                        "Line 9: Description is missing",
                        "Line 10: expected 7 columns but found 4"));
    }

    @Test
    void rejectsFilesFromOtherBanks() {
        assertThatThrownBy(() -> parser.parse("Date,Amount,Description\n2026-09-01,-5.00,COFFEE\n"))
                .isInstanceOfSatisfying(StatementParseException.class, e -> assertThat(e.getErrors().getFirst())
                        .startsWith("This doesn't look like a Capital One credit card export")
                        .contains("Transaction Date", "Debit", "Credit"));
    }

    @Test
    void rejectsEmptyFileAndHeaderOnlyFile() {
        assertThatThrownBy(() -> parser.parse("")).isInstanceOf(StatementParseException.class);
        assertThatThrownBy(() -> parser.parse(HEADER))
                .isInstanceOfSatisfying(StatementParseException.class,
                        e -> assertThat(e.getErrors()).containsExactly("The file has no transactions."));
    }

    @Test
    void rejectsUnclosedQuote() {
        assertThatThrownBy(() -> parser.parse(HEADER + "2026-09-01,2026-09-02,1234,\"BROKEN,Other,1.00,\n"))
                .isInstanceOfSatisfying(StatementParseException.class,
                        e -> assertThat(e.getErrors()).containsExactly("The file is not valid CSV."));
    }

    @Test
    void stopsAfterTooManyErrors() {
        String badRows = "not-a-date,2026-09-02,1234,X,Other,1.00,\n".repeat(50);

        assertThatThrownBy(() -> parser.parse(HEADER + badRows))
                .isInstanceOfSatisfying(StatementParseException.class, e -> {
                    assertThat(e.getErrors()).hasSize(CapitalOneCreditCardParser.MAX_REPORTED_ERRORS + 1);
                    assertThat(e.getErrors().getLast()).isEqualTo("Too many errors; stopped checking.");
                });
    }

    @Test
    void rejectsMoreThanMaxRows() {
        String rows = "2026-09-01,2026-09-02,1234,X,Other,1.00,\n".repeat(CapitalOneCreditCardParser.MAX_ROWS + 1);

        assertThatThrownBy(() -> parser.parse(HEADER + rows))
                .isInstanceOfSatisfying(StatementParseException.class,
                        e -> assertThat(e.getErrors().getFirst()).contains("more than 10,000 transactions"));
    }
}
