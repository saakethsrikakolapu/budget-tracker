package com.saaketh.budget.common;

/** The request itself doesn't make sense (e.g. a date range that ends before it starts). */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
