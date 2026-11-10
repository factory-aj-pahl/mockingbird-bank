package com.mockingbirdbank.ui.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mockingbirdbank.resilience.ServiceUnavailableException;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;

class ProgramLookupTest {

    @Test
    void returnsWhatTheLookupReturns() {
        assertThat(ProgramLookup.orUnavailable(() -> List.of("Regular")))
                .contains(List.of("Regular"));
    }

    @Test
    void openBreakerMeansUnavailable() {
        assertThat(
                        ProgramLookup.orUnavailable(
                                failing(new ServiceUnavailableException("open", null))))
                .isEmpty();
    }

    @Test
    void databaseErrorsBeforeTheBreakerOpensMeanUnavailable() {
        assertThat(
                        ProgramLookup.orUnavailable(
                                failing(new DataAccessResourceFailureException("down"))))
                .isEmpty();
        assertThat(
                        ProgramLookup.orUnavailable(
                                failing(new CannotCreateTransactionException("no connection"))))
                .isEmpty();
        assertThat(
                        ProgramLookup.orUnavailable(
                                failing(new PersistenceException("lost connection"))))
                .isEmpty();
    }

    @Test
    void otherFailuresStillPropagate() {
        IllegalStateException bug = new IllegalStateException("bug");

        assertThatThrownBy(() -> ProgramLookup.orUnavailable(failing(bug))).isSameAs(bug);
    }

    private static Supplier<List<String>> failing(RuntimeException failure) {
        return () -> {
            throw failure;
        };
    }
}
