package com.akhana.akhana_admin.dto;

import java.time.Instant;

public record ErrorResponse(
    String error,
    String message,
    Instant timestamp
) {
    // INV-10: a sale or consumption with more than the recorded stock, not yet confirmed
    public static final String INSUFFICIENT_STOCK = "Insufficient Stock";

    public static ErrorResponse unauthorized(String message) {
        return new ErrorResponse("Unauthorized", message, Instant.now());
    }

    public static ErrorResponse forbidden(String message) {
        return new ErrorResponse("Forbidden", message, Instant.now());
    }

    public static ErrorResponse badRequest(String message) {
        return new ErrorResponse("Bad Request", message, Instant.now());
    }

    public static ErrorResponse conflict(String message) {
        return new ErrorResponse("Conflict", message, Instant.now());
    }

    public static ErrorResponse tooManyRequests(String message) {
        return new ErrorResponse("Too Many Requests", message, Instant.now());
    }

    public static ErrorResponse internalError(String message) {
        return new ErrorResponse("Internal Server Error", message, Instant.now());
    }

    public static ErrorResponse methodNotAllowed(String message) {
        return new ErrorResponse("Method Not Allowed", message, Instant.now());
    }

    public static ErrorResponse notFound(String message) {
        return new ErrorResponse("Not Found", message, Instant.now());
    }
}
