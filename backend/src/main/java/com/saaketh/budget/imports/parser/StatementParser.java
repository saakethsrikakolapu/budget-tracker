package com.saaketh.budget.imports.parser;

import java.util.List;

/**
 * Turns the text of a bank statement file into transactions. One implementation per bank format
 * (Strategy pattern); Stage 3 adds a configurable one so any bank's CSV works.
 */
public interface StatementParser {

    /**
     * @throws StatementParseException listing every problem found, if any row is invalid.
     *     All-or-nothing: either every row parses, or nothing is returned.
     */
    List<ParsedTransaction> parse(String csvText);
}
