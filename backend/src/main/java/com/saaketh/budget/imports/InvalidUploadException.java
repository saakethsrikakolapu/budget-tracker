package com.saaketh.budget.imports;

/** The uploaded file itself is unacceptable (wrong type, empty, not UTF-8...). */
public class InvalidUploadException extends RuntimeException {

    public InvalidUploadException(String message) {
        super(message);
    }
}
