package com.saaketh.budget.common;

/** Something doesn't exist, or belongs to another user (we deliberately don't say which). */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
