package com.mockingbirdbank.ui.view;

import com.mockingbirdbank.analytics.AnalyticsEventService;
import com.mockingbirdbank.resilience.ServiceUnavailableException;
import jakarta.persistence.PersistenceException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;

/**
 * Reads programs for an optional page section. Programs sit next to the accounts rather than
 * replacing them, so when they can't be read the section shows {@link ProgramCard#UNAVAILABLE} and
 * the rest of the page still renders.
 *
 * <p>{@link ServiceUnavailableException} alone only covers an open {@code programs} circuit
 * breaker. Before it opens, and on a failed half-open trial, the database error itself arrives:
 * translated by the repository ({@link DataAccessException}), from the {@code @Transactional} proxy
 * around {@code ProgramService} before the breaker sees the call ({@link TransactionException}), or
 * untranslated from Hibernate ({@link PersistenceException}). These types also cover failures that
 * are bugs rather than outages, such as a query against a missing table or a Hibernate mapping
 * error. They get the same fallback, because a programs failure must not take the accounts down
 * with it. Every fallback is tracked as {@code programs.unavailable} with the exception's simple
 * class name as the reason, and the database errors are logged at WARN with their stack trace, so a
 * bug still shows up as an error count. Exceptions outside these types still propagate to {@code
 * GlobalErrorHandler}.
 */
final class ProgramLookup {

    private static final Logger log = LoggerFactory.getLogger(ProgramLookup.class);

    private ProgramLookup() {}

    /** The lookup's result, or empty when programs can't be read right now. */
    static <T> Optional<T> orUnavailable(AnalyticsEventService analytics, Supplier<T> lookup) {
        try {
            return Optional.of(lookup.get());
        } catch (ServiceUnavailableException e) {
            return unavailable(analytics, e);
        } catch (DataAccessException | TransactionException | PersistenceException e) {
            log.warn("Programs could not be read; showing them as unavailable", e);
            return unavailable(analytics, e);
        }
    }

    private static <T> Optional<T> unavailable(
            AnalyticsEventService analytics, RuntimeException failure) {
        analytics.trackError("programs.unavailable", failure.getClass().getSimpleName(), Map.of());
        return Optional.empty();
    }
}
