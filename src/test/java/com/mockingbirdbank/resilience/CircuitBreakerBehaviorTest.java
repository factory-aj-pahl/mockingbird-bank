package com.mockingbirdbank.resilience;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mockingbirdbank.analytics.AnalyticsEventService;
import com.mockingbirdbank.repository.AccountRepository;
import com.mockingbirdbank.repository.AppUserRepository;
import com.mockingbirdbank.repository.TransactionRepository;
import com.mockingbirdbank.service.AccountService;
import com.mockingbirdbank.service.TransactionService;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * Exercises the real {@link ResilienceConfig#defaultConfig()} against a repository that always
 * fails, rather than asserting anything about Resilience4j's internals directly - if this test
 * passes, a genuinely struggling Postgres would stop piling up connections/threads in production
 * too.
 */
class CircuitBreakerBehaviorTest {

    @Test
    void repeatedRepositoryFailuresTripTheBreakerOpenAndThenFailFastWithoutHittingTheRepository() {
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        AccountRepository accountRepository = mock(AccountRepository.class);
        when(accountRepository.findById(1L))
                .thenThrow(new DataAccessResourceFailureException("db is down"));

        AccountService accountService =
                new AccountService(
                        appUserRepository,
                        accountRepository,
                        CircuitBreakerRegistry.of(ResilienceConfig.defaultConfig()),
                        new AnalyticsEventService(new SimpleMeterRegistry()));

        // minimumNumberOfCalls(10): each of the first 10 calls genuinely reaches the (failing)
        // repository - one real attempt per call, exactly as it should before there's enough
        // history to judge a failure rate from.
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> accountService.requireAccount(1L))
                    .isInstanceOf(DataAccessResourceFailureException.class);
        }
        verify(accountRepository, times(10)).findById(1L);

        // 100% failure rate over that window trips the breaker open - the 11th call fails fast
        // as ServiceUnavailableException, and the repository mock's call count doesn't move.
        assertThatThrownBy(() -> accountService.requireAccount(1L))
                .isInstanceOf(ServiceUnavailableException.class);
        verify(accountRepository, times(10)).findById(1L);
    }

    @Test
    void sameBehaviorForTransactionServiceGuardingItsOwnIndependentlyNamedCircuitBreaker() {
        TransactionRepository transactionRepository = mock(TransactionRepository.class);
        when(transactionRepository.findByAccountIdOrderByPostedAtDesc(1L))
                .thenThrow(new DataAccessResourceFailureException("db is down"));

        TransactionService transactionService =
                new TransactionService(
                        transactionRepository,
                        CircuitBreakerRegistry.of(ResilienceConfig.defaultConfig()));

        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> transactionService.forAccount(1L))
                    .isInstanceOf(DataAccessResourceFailureException.class);
        }
        verify(transactionRepository, times(10)).findByAccountIdOrderByPostedAtDesc(1L);

        assertThatThrownBy(() -> transactionService.forAccount(1L))
                .isInstanceOf(ServiceUnavailableException.class);
        verify(transactionRepository, times(10)).findByAccountIdOrderByPostedAtDesc(1L);
    }
}
