package com.saaketh.budget.auth;

public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("An account with this email already exists");
    }
}
