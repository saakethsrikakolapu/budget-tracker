package com.saaketh.budget.imports.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saaketh.budget.imports.csv.ColumnMapping.AmountStyle;
import com.saaketh.budget.imports.parser.ParsedTransaction;
import com.saaketh.budget.imports.parser.StatementParseException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The inference engine against fake files shaped like each bank's reported export (samples/).
 * Nothing in the engine knows about these banks; it must work them out from the values.
 */
class ColumnInferenceTest {

    private static final Path SAMPLES = Path.of("..", "samples");

    /**
     * file, description column name, number of transactions, style, date of the coffee purchase,
     * a money-in description, its amount, bank category of the coffee row (or null)
     */
    static Stream<Arguments> layouts() {
        return Stream.of(
                Arguments.of("capital-one-credit-card.csv", "Description", 10, AmountStyle.DEBIT_CREDIT, "2026-09-28", "AUTOPAY", "250.00", "Dining"),
                Arguments.of("layouts/chase-credit-card.csv", "Description", 8, AmountStyle.SIGNED, "2026-10-28", "AUTOMATIC PAYMENT", "250.00", "Food & Drink"),
                Arguments.of("layouts/chase-checking.csv", "Description", 7, AmountStyle.SIGNED, "2026-10-28", "PAYROLL", "400.00", null),
                Arguments.of("layouts/bank-of-america-checking.csv", "Description", 7, AmountStyle.SIGNED, "2026-10-02", "PAYROLL", "400.00", null),
                Arguments.of("layouts/wells-fargo.csv", "Column 5", 7, AmountStyle.SIGNED, "2026-10-28", "PAYROLL", "400.00", null),
                Arguments.of("layouts/discover.csv", "Description", 7, AmountStyle.SIGNED, "2026-10-28", "INTERNET PAYMENT", "250.00", "Restaurants"),
                Arguments.of("layouts/american-express.csv", "Description", 6, AmountStyle.SIGNED, "2026-10-28", "AUTOPAY", "250.00", "Restaurant-Restaurant"),
                Arguments.of("layouts/citi.csv", "Description", 7, AmountStyle.DEBIT_CREDIT, "2026-10-28", "ONLINE PAYMENT", "250.00", null),
                Arguments.of("layouts/us-bank.csv", "Name", 6, AmountStyle.SIGNED, "2026-10-28", "PAYROLL", "400.00", null),
                Arguments.of("layouts/pnc.csv", "Description", 6, AmountStyle.DEBIT_CREDIT, "2026-10-28", "PAYROLL", "400.00", null),
                Arguments.of("layouts/apple-card.csv", "Description", 6, AmountStyle.SIGNED, "2026-10-28", "ACH DEPOSIT", "250.00", "Restaurants"),
                Arguments.of("layouts/capital-one-360-checking.csv", "Transaction Description", 6, AmountStyle.UNSIGNED_WITH_TYPE, "2026-10-28", "PAYROLL", "400.00", null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("layouts")
    void readsEveryLayoutFromItsValues(String file, String descriptionColumn, int count, AmountStyle style,
            String coffeeDate, String moneyInText, String moneyInAmount, String coffeeCategory) throws Exception {
        CsvTable table = CsvTable.read(Files.readString(SAMPLES.resolve(file)));

        ColumnInference.Result result = ColumnInference.infer(table);
        ColumnMapping mapping = result.mapping();
        List<ParsedTransaction> transactions = MappedCsvParser.parse(table, mapping).transactions();

        assertThat(result.columnNames().get(mapping.descriptionColumn())).isEqualTo(descriptionColumn);
        assertThat(mapping.amountStyle()).isEqualTo(style);
        assertThat(transactions).hasSize(count);

        ParsedTransaction coffee = find(transactions, "CAMPUS COFFEE");
        assertThat(coffee.amount()).as("a purchase is spending (negative)").isEqualByComparingTo("-4.75");
        assertThat(coffee.transactionDate()).isEqualTo(LocalDate.parse(coffeeDate));
        assertThat(coffee.bankCategory()).isEqualTo(coffeeCategory);

        ParsedTransaction moneyIn = find(transactions, moneyInText);
        assertThat(moneyIn.amount()).as("payments and deposits are money in (positive)")
                .isEqualByComparingTo(new BigDecimal(moneyInAmount));
    }

    @Test
    void earlierOfTwoDatesIsTheTransactionDate() throws Exception {
        CsvTable table = CsvTable.read(Files.readString(SAMPLES.resolve("layouts/discover.csv")));

        ColumnMapping mapping = ColumnInference.infer(table).mapping();

        assertThat(mapping.dateColumn()).isZero();       // Trans. Date
        assertThat(mapping.postedDateColumn()).isEqualTo(1); // Post Date
    }

    @Test
    void worksWithoutHeaderNamesOrWithMisleadingOnes() {
        // Same data three ways: no header, nonsense header names, and columns in a different order.
        String rows = """
                09/01/2026,COFFEE SHOP DOWNTOWN,-4.75
                09/02/2026,GROCERY STORE 12,-48.33
                09/03/2026,PAYROLL DEPOSIT,400.00
                09/04/2026,BOOKSTORE,-62.10
                """;
        for (String csv : List.of(rows, "Foo,Bar,Baz\n" + rows, reorder(rows))) {
            CsvTable table = CsvTable.read(csv);
            List<ParsedTransaction> parsed = MappedCsvParser.parse(table, ColumnInference.infer(table).mapping()).transactions();

            assertThat(parsed).hasSize(4);
            assertThat(find(parsed, "COFFEE").amount()).isEqualByComparingTo("-4.75");
            assertThat(find(parsed, "PAYROLL").amount()).isEqualByComparingTo("400.00");
        }
    }

    @Test
    void purchasesWrittenAsPositiveAreFlippedToSpending() {
        CsvTable table = CsvTable.read("""
                Date,Description,Amount
                09/01/2026,COFFEE,4.75
                09/02/2026,GROCERIES,48.33
                09/03/2026,BOOKS,62.10
                09/04/2026,PAYMENT THANK YOU,-100.00
                """);

        ColumnMapping mapping = ColumnInference.infer(table).mapping();
        List<ParsedTransaction> parsed = MappedCsvParser.parse(table, mapping).transactions();

        assertThat(mapping.positiveIsSpending()).isTrue();
        assertThat(find(parsed, "COFFEE").amount()).isEqualByComparingTo("-4.75");
        assertThat(find(parsed, "PAYMENT").amount()).isEqualByComparingTo("100.00");
    }

    @Test
    void ignoresIdNumbersAndRunningBalances() throws Exception {
        CsvTable table = CsvTable.read(Files.readString(SAMPLES.resolve("layouts/chase-checking.csv")));

        ColumnInference.Result result = ColumnInference.infer(table);

        assertThat(result.columnNames().get(result.mapping().amountColumn())).isEqualTo("Amount"); // not Balance
    }

    @Test
    void explainsWhatItCouldNotFind() {
        assertThatThrownBy(() -> ColumnInference.infer(CsvTable.read("Name,Note\nAlice,hello\nBob,hi\n")))
                .isInstanceOfSatisfying(StatementParseException.class,
                        e -> assertThat(e.getErrors().getFirst()).contains("no rows have both a date and an amount"));
    }

    private static String reorder(String rows) {
        StringBuilder sb = new StringBuilder();
        for (String line : rows.strip().split("\n")) {
            String[] c = line.split(",");
            sb.append(c[2]).append(',').append(c[1]).append(',').append(c[0]).append('\n');
        }
        return sb.toString();
    }

    private static ParsedTransaction find(List<ParsedTransaction> transactions, String text) {
        return transactions.stream()
                .filter(t -> t.description().toUpperCase(Locale.ROOT).contains(text))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no transaction containing " + text));
    }
}
