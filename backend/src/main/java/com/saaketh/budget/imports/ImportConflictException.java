package com.saaketh.budget.imports;

/** Two uploads into the same account collided; the second one is rolled back. */
public class ImportConflictException extends RuntimeException {

    public ImportConflictException() {
        super("Another upload into this account finished at the same moment. Try again.");
    }
}
