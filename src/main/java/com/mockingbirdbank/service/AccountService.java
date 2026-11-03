package com.mockingbirdbank.service;

import com.mockingbirdbank.analytics.AnalyticsEventService;
import com.mockingbirdbank.model.Account;
import com.mockingbirdbank.model.AccountHolder;
import com.mockingbirdbank.model.AppUser;
import com.mockingbirdbank.repository.AccountRepository;
import com.mockingbirdbank.repository.AppUserRepository;
import com.mockingbirdbank.resilience.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AccountService {

    private static final String REVEAL_EVENT = "account_number.revealed";

    private final AppUserRepository appUserRepository;
    private final AccountRepository accountRepository;
    private final CircuitBreaker circuitBreaker;
    private final AnalyticsEventService analytics;

    public AccountService(
            AppUserRepository appUserRepository,
            AccountRepository accountRepository,
            CircuitBreakerRegistry circuitBreakerRegistry,
            AnalyticsEventService analytics) {
        this.appUserRepository = appUserRepository;
        this.accountRepository = accountRepository;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("accounts");
        this.analytics = analytics;
    }

    /** Resolves the holder linked to whoever Spring Security says is signed in. */
    public AccountHolder currentHolder() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<AppUser> appUser = protectedCall(() -> appUserRepository.findByUsername(username));
        return appUser.map(AppUser::getHolder)
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "No account holder linked to user: " + username));
    }

    public List<Account> accountsFor(AccountHolder holder) {
        return protectedCall(
                () -> accountRepository.findByHolderIdOrderByAccountTypeAscIdAsc(holder.getId()));
    }

    public Account requireAccount(Long accountId) {
        Optional<Account> account = protectedCall(() -> accountRepository.findById(accountId));
        return account.orElseThrow(
                () -> new IllegalArgumentException("No such account: " + accountId));
    }

    /**
     * Full, unmasked account number for one of the signed-in holder's own accounts, fetched on
     * explicit user request only. An account that doesn't exist and one owned by someone else both
     * fail the same way, so a caller can't probe which account IDs exist. Every reveal is recorded
     * as an analytics event; the number itself is never logged.
     */
    public String revealAccountNumber(Long accountId) {
        AccountHolder holder = currentHolder();
        Optional<Account> account = protectedCall(() -> accountRepository.findById(accountId));
        Account owned =
                account.filter(candidate -> isOwnedBy(candidate, holder))
                        .orElseThrow(
                                () -> {
                                    analytics.trackError(
                                            REVEAL_EVENT,
                                            "forbidden",
                                            Map.of("accountId", String.valueOf(accountId)));
                                    return new AccessDeniedException("Account not available");
                                });
        analytics.track(
                REVEAL_EVENT, Map.of("accountId", String.valueOf(accountId), "scope", "single"));
        return owned.getAccountNumber();
    }

    /**
     * Full account numbers for every account the signed-in holder owns, keyed by account ID - one
     * call backing the dashboard's "show all" toggle rather than one round trip per account.
     */
    public Map<Long, String> revealAccountNumbers() {
        List<Account> accounts = accountsFor(currentHolder());
        Map<Long, String> numbers = new LinkedHashMap<>();
        accounts.forEach(account -> numbers.put(account.getId(), account.getAccountNumber()));
        analytics.track(
                REVEAL_EVENT,
                Map.of("scope", "all", "accountCount", String.valueOf(numbers.size())));
        return numbers;
    }

    private static boolean isOwnedBy(Account account, AccountHolder holder) {
        return account.getHolder() != null
                && holder.getId() != null
                && Objects.equals(account.getHolder().getId(), holder.getId());
    }

    /**
     * Only wraps the actual repository call, never the {@code orElseThrow} above it - a business
     * outcome like "no such account" must not count as a circuit-breaker failure the way a real
     * database error does.
     */
    private <T> T protectedCall(Supplier<T> repositoryCall) {
        try {
            return circuitBreaker.executeSupplier(repositoryCall);
        } catch (CallNotPermittedException e) {
            throw new ServiceUnavailableException("Accounts are temporarily unavailable", e);
        }
    }

    public BigDecimal totalBalance(AccountHolder holder) {
        return accountsFor(holder).stream()
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
