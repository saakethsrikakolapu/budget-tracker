package com.saaketh.budget.imports.parser;

import java.util.List;

/** The file couldn't be imported; {@link #getErrors()} says why, e.g. "Line 7: invalid date". */
public class StatementParseException extends RuntimeException {

    private final List<String> errors;

    public StatementParseException(List<String> errors) {
        super("The file could not be imported");
        this.errors = List.copyOf(errors);
    }

    public StatementParseException(String error) {
        this(List.of(error));
    }

    public List<String> getErrors() {
        return errors;
    }
}
