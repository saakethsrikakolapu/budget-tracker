package com.saaketh.budget.category;

public class RulePatternTakenException extends RuntimeException {

    public RulePatternTakenException() {
        super("You already have a rule for this text");
    }
}
