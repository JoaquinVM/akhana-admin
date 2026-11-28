package com.akhana.akhana_admin.exception;

/**
 * INV-10: a sale, a sale at cost or a consumption takes more than the recorded stock and was not confirmed. The screen
 * shows the message and asks whether to go on anyway.
 */
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String message) {
        super(message);
    }
}
