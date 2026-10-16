package com.saaketh.budget.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** One CSV cell in "Download my data": escaped the CSV way, and never run as a spreadsheet formula. */
class TransactionExportCellTest {

    @Test
    void plainTextIsUnchanged() {
        assertThat(TransactionExportController.cell("CHIPOTLE 1234")).isEqualTo("CHIPOTLE 1234");
        assertThat(TransactionExportController.cell(null)).isEmpty();
    }

    @Test
    void commasAndQuotesAreQuoted() {
        assertThat(TransactionExportController.cell("Chase, Freedom")).isEqualTo("\"Chase, Freedom\"");
        assertThat(TransactionExportController.cell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
    }

    @Test
    void formulasStayText() {
        assertThat(TransactionExportController.cell("=1+1")).isEqualTo("'=1+1");
        assertThat(TransactionExportController.cell("+1 555 0100")).isEqualTo("'+1 555 0100");
        assertThat(TransactionExportController.cell("-HYPERLINK")).isEqualTo("'-HYPERLINK");
        assertThat(TransactionExportController.cell("@SUM(A1)")).isEqualTo("'@SUM(A1)");
    }
}
