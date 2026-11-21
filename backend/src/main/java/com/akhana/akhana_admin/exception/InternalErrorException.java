package com.akhana.akhana_admin.exception;

/** An error of the system itself, not of what the person did: the client gets the generic message, the log the trace. */
public class InternalErrorException extends RuntimeException {

    public InternalErrorException(String message) {
        super(message);
    }

    public InternalErrorException(String message, Throwable cause) {
        super(message, cause);
    }
}
