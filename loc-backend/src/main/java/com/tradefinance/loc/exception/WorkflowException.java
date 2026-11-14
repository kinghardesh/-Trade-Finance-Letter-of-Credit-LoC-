package com.tradefinance.loc.exception;

/** Thrown when Camunda cannot be reached or rejects a command → HTTP 503. */
public class WorkflowException extends RuntimeException {

    public WorkflowException(String message, Throwable cause) {
        super(message, cause);
    }
}
