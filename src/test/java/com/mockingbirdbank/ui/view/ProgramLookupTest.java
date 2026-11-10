package com.mockingbirdbank.ui.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mockingbirdbank.analytics.AnalyticsEventService;
import com.mockingbirdbank.resilience.ServiceUnavailableException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;

class ProgramLookupTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AnalyticsEventService analytics = new AnalyticsEventService(registry);

    @Test
    void returnsWhatTheLookupReturns() {
        assertThat(ProgramLookup.orUnavailable(analytics, () -> List.of("Regular")))
                .contains(List.of("Regular"));
        assertThat(registry.find("product.events").counters()).isEmpty();
    }

    @Test
    void openBreakerMeansUnavailable() {
        assertThat(
                        ProgramLookup.orUnavailable(
                                analytics, failing(new ServiceUnavailableException("open", null))))
                .isEmpty();
        assertThat(unavailableCount("ServiceUnavailableException")).isEqualTo(1.0);
    }

    @Test
    void databaseErrorsBeforeTheBreakerOpensMeanUnavailable() {
        assertThat(
                        ProgramLookup.orUnavailable(
                                analytics, failing(new DataAccessResourceFailureException("down"))))
                .isEmpty();
        assertThat(
                        ProgramLookup.orUnavailable(
                                analytics,
                                failing(new CannotCreateTransactionException("no connection"))))
                .isEmpty();
        assertThat(
                        ProgramLookup.orUnavailable(
                                analytics, failing(new PersistenceException("lost connection"))))
                .isEmpty();

        assertThat(unavailableCount("DataAccessResourceFailureException")).isEqualTo(1.0);
        assertThat(unavailableCount("CannotCreateTransactionException")).isEqualTo(1.0);
        assertThat(unavailableCount("PersistenceException")).isEqualTo(1.0);
    }

    @Test
    void otherFailuresStillPropagate() {
        IllegalStateException bug = new IllegalStateException("bug");

        assertThatThrownBy(() -> ProgramLookup.orUnavailable(analytics, failing(bug)))
                .isSameAs(bug);
        assertThat(registry.find("product.events").counters()).isEmpty();
    }

    private double unavailableCount(String reason) {
        Counter counter =
                registry.get("product.events")
                        .tags("event", "programs.unavailable", "outcome", "error", "reason", reason)
                        .counter();
        return counter.count();
    }

    private static Supplier<List<String>> failing(RuntimeException failure) {
        return () -> {
            throw failure;
        };
    }
}
