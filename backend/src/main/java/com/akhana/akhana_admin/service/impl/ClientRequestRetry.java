package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.exception.DuplicateResourceException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * POS-18, COM-27: a new sale or purchase carries the id its screen made, and a repeated id gets the existing record
 * back. The check before saving covers the retries; two requests with the same id at the same time are settled by the
 * unique index: the loser's transaction is rolled back and the work runs again in a new one, which finds the winner.
 */
final class ClientRequestRetry {

    private ClientRequestRetry() {
    }

    /** Thrown when the unique index of the client request id rejected the save. */
    static final class TakenException extends DuplicateResourceException {
        TakenException() {
            super("Otra pantalla registró lo mismo al mismo tiempo; revise antes de volver a intentar.");
        }
    }

    /** Rethrows the error as {@link TakenException} when it comes from the given unique index. */
    static RuntimeException translate(DataIntegrityViolationException e, String indexName) {
        String cause = e.getMostSpecificCause().getMessage();
        return cause != null && cause.contains(indexName) ? new TakenException() : e;
    }

    static <T> T run(TransactionTemplate transactionTemplate, Supplier<T> work) {
        try {
            return transactionTemplate.execute(status -> work.get());
        } catch (TakenException e) {
            // Inside a caller's transaction it cannot be retried: the database already aborted it
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                throw e;
            }
            return transactionTemplate.execute(status -> work.get());
        }
    }
}
