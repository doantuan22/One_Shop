package com.oneshop.exception;

public class BadRequestException extends RuntimeException {

    private final String field;

    public BadRequestException(String message) {
        this(null, message);
    }

    /** @param field name of the request field that is wrong, so a form can show the message next to it */
    public BadRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    /** The offending request field, or {@code null} when the error is not about one field. */
    public String getField() {
        return field;
    }
}
