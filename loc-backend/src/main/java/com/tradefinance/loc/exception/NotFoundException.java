package com.tradefinance.loc.exception;

/** Thrown when an application or task does not exist → HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
