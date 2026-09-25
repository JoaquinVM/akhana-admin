package com.akhana.akhana_admin.dto;

import java.time.Instant;

public record ErrorResponse(
    String error,
    String message,
    Instant timestamp
) {
    public static ErrorResponse unauthorized(String message) {
        return new ErrorResponse("Unauthorized", message, Instant.now());
    }

    public static ErrorResponse badRequest(String message) {
        return new ErrorResponse("Bad Request", message, Instant.now());
    }
}
