package com.mockingbirdbank.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mockingbirdbank.analytics.AnalyticsEventService;
import com.mockingbirdbank.model.Account;
import com.mockingbirdbank.model.AccountHolder;
import com.mockingbirdbank.model.AccountType;
import com.mockingbirdbank.model.AppUser;
import com.mockingbirdbank.repository.AccountRepository;
import com.mockingbirdbank.repository.AppUserRepository;
import com.mockingbirdbank.resilience.ResilienceConfig;
import com.mockingbirdbank.resilience.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock private AppUserRepository appUserRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private AnalyticsEventService analytics;

    private CircuitBreakerRegistry circuitBreakerRegistry;
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry = CircuitBreakerRegistry.of(ResilienceConfig.defaultConfig());
        accountService =
                new AccountService(
                        appUserRepository, accountRepository, circuitBreakerRegistry, analytics);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String username) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(username, "n/a", List.of()));
    }

    @Test
    void currentHolderResolvesHolderLinkedToSignedInUser() {
        AccountHolder holder = new AccountHolder("Jordan Ellis", "jordan.ellis@example.com");
        AppUser appUser = new AppUser("jordan.ellis", "hashed", holder);
        authenticateAs("jordan.ellis");
        when(appUserRepository.findByUsername("jordan.ellis")).thenReturn(Optional.of(appUser));

        assertThat(accountService.currentHolder()).isSameAs(holder);
    }

    @Test
    void currentHolderFailsFastWhenSignedInUserHasNoLinkedAccount() {
        authenticateAs("ghost");
        when(appUserRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(accountService::currentHolder).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void totalBalanceSumsAllAccountsForHolder() {
        AccountHolder holder = new AccountHolder("Jordan Ellis", "jordan.ellis@example.com");
        holder.setId(1L);
        Account checking =
                new Account(
                        holder,
                        "1",
                        AccountType.CHECKING,
                        "Checking",
                        new BigDecimal("100.00"),
                        LocalDate.now());
        Account savings =
                new Account(
                        holder,
                        "2",
                        AccountType.SAVINGS,
                        "Savings",
                        new BigDecimal("250.50"),
                        LocalDate.now());
        when(accountRepository.findByHolderIdOrderByAccountTypeAscIdAsc(1L))
                .thenReturn(List.of(checking, savings));

        assertThat(accountService.totalBalance(holder)).isEqualByComparingTo("350.50");
    }

    @Test
    void requireAccountThrowsForUnknownId() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.requireAccount(99L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requireAccountReturnsMatchFromRepository() {
        Account account =
                new Account(
                        new AccountHolder("Jordan Ellis", "jordan.ellis@example.com"),
                        "1",
                        AccountType.CHECKING,
                        "Checking",
                        BigDecimal.ZERO,
                        LocalDate.now());
        when(accountRepository.findById(7L)).thenReturn(Optional.of(account));

        assertThat(accountService.requireAccount(7L)).isSameAs(account);
    }

    private AccountHolder signedInHolder(long holderId) {
        AccountHolder holder = new AccountHolder("Jordan Ellis", "jordan.ellis@example.com");
        holder.setId(holderId);
        authenticateAs("jordan.ellis");
        when(appUserRepository.findByUsername("jordan.ellis"))
                .thenReturn(Optional.of(new AppUser("jordan.ellis", "hashed", holder)));
        return holder;
    }

    private static Account accountOwnedBy(AccountHolder holder, long id, String number) {
        Account account =
                new Account(
                        holder,
                        number,
                        AccountType.CHECKING,
                        "Checking",
                        BigDecimal.ZERO,
                        LocalDate.now());
        account.setId(id);
        return account;
    }

    @Test
    void revealAccountNumberReturnsFullNumberForOwnAccountAndRecordsEvent() {
        AccountHolder holder = signedInHolder(1L);
        when(accountRepository.findById(7L))
                .thenReturn(Optional.of(accountOwnedBy(holder, 7L, "1000000004821")));

        assertThat(accountService.revealAccountNumber(7L)).isEqualTo("1000000004821");
        verify(analytics)
                .track("account_number.revealed", Map.of("accountId", "7", "scope", "single"));
    }

    @Test
    void revealAccountNumberDeniesAccountOwnedBySomeoneElse() {
        signedInHolder(1L);
        AccountHolder stranger = new AccountHolder("Someone Else", "someone@example.com");
        stranger.setId(2L);
        when(accountRepository.findById(8L))
                .thenReturn(Optional.of(accountOwnedBy(stranger, 8L, "9999999999999")));

        assertThatThrownBy(() -> accountService.revealAccountNumber(8L))
                .isInstanceOf(AccessDeniedException.class);
        verify(analytics)
                .trackError("account_number.revealed", "forbidden", Map.of("accountId", "8"));
    }

    @Test
    void revealAccountNumberDeniesUnknownAccountTheSameWay() {
        signedInHolder(1L);
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.revealAccountNumber(99L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void revealAccountNumbersReturnsOnlySignedInHoldersAccounts() {
        AccountHolder holder = signedInHolder(1L);
        when(accountRepository.findByHolderIdOrderByAccountTypeAscIdAsc(1L))
                .thenReturn(
                        List.of(
                                accountOwnedBy(holder, 7L, "1000000004821"),
                                accountOwnedBy(holder, 9L, "2000000007743")));

        assertThat(accountService.revealAccountNumbers())
                .containsExactly(Map.entry(7L, "1000000004821"), Map.entry(9L, "2000000007743"));
        verify(analytics)
                .track("account_number.revealed", Map.of("scope", "all", "accountCount", "2"));
    }

    @Test
    void revealAccountNumberFailsFastWhenCircuitBreakerIsOpen() {
        authenticateAs("jordan.ellis");
        circuitBreakerRegistry.circuitBreaker("accounts").transitionToOpenState();

        assertThatThrownBy(() -> accountService.revealAccountNumber(7L))
                .isInstanceOf(ServiceUnavailableException.class);
    }
}
