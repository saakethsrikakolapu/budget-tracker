package com.saaketh.budget.account;

public class AccountNameTakenException extends RuntimeException {

    public AccountNameTakenException() {
        super("You already have an account with this name");
    }
}
