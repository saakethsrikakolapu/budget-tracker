package com.saaketh.budget.imports.csv;

/**
 * Which column holds what, by 0-based column position. Produced by {@link ColumnInference},
 * shown in the preview, editable by the user, and saved to remember a file's format.
 *
 * @param postedDateColumn may be null
 * @param categoryColumn the bank's own category label column; may be null
 * @param amountColumn used by SIGNED and UNSIGNED_WITH_TYPE
 * @param debitColumn money out, used by DEBIT_CREDIT
 * @param creditColumn money in, used by DEBIT_CREDIT
 * @param typeColumn e.g. "Debit"/"Credit" words, used by UNSIGNED_WITH_TYPE
 * @param positiveIsSpending for SIGNED: true if purchases appear as positive numbers
 * @param dateFormat a pattern from {@link Cells#DATE_PATTERNS}
 */
public record ColumnMapping(
        int dateColumn,
        Integer postedDateColumn,
        int descriptionColumn,
        Integer categoryColumn,
        AmountStyle amountStyle,
        Integer amountColumn,
        Integer debitColumn,
        Integer creditColumn,
        Integer typeColumn,
        boolean positiveIsSpending,
        String dateFormat) {

    public enum AmountStyle {
        /** One column; the sign says spending vs money in (which sign is which varies by bank). */
        SIGNED,
        /** Two columns: one for money out, one for money in; each row fills one. */
        DEBIT_CREDIT,
        /** One column of positive numbers, plus a column saying "Debit"/"Credit" (or similar). */
        UNSIGNED_WITH_TYPE
    }
}
