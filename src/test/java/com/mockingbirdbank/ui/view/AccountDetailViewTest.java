package com.mockingbirdbank.ui.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mockingbirdbank.analytics.AnalyticsEventService;
import com.mockingbirdbank.config.FeatureFlagService;
import com.mockingbirdbank.config.FeatureFlagsProperties;
import com.mockingbirdbank.model.Account;
import com.mockingbirdbank.model.AccountHolder;
import com.mockingbirdbank.model.AccountType;
import com.mockingbirdbank.service.AccountService;
import com.mockingbirdbank.service.ProgramService;
import com.mockingbirdbank.service.TransactionService;
import com.vaadin.flow.router.NotFoundException;
import com.vaadin.flow.router.RouteConfiguration;
import com.vaadin.flow.server.VaadinServletContext;
import com.vaadin.flow.server.startup.ApplicationRouteRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockServletContext;

class AccountDetailViewTest {

    private final AccountService accountService = mock(AccountService.class);
    private final TransactionService transactionService = mock(TransactionService.class);
    private final AnalyticsEventService analytics = mock(AnalyticsEventService.class);
    private final ProgramService programService = mock(ProgramService.class);
    private final AccountDetailView view =
            new AccountDetailView(
                    accountService,
                    transactionService,
                    analytics,
                    new FeatureFlagService(new FeatureFlagsProperties()),
                    programService);

    @Test
    void everySingleSegmentUnderAccountsIsThisRouteButLongerPathsAreNot() {
        ApplicationRouteRegistry registry =
                ApplicationRouteRegistry.getInstance(
                        new VaadinServletContext(new MockServletContext()));
        RouteConfiguration.forRegistry(registry).setAnnotatedRoute(AccountDetailView.class);

        assertThat(registry.getNavigationTarget("accounts/7")).contains(AccountDetailView.class);
        assertThat(registry.getNavigationTarget("accounts/not-a-number"))
                .contains(AccountDetailView.class);
        assertThat(registry.getNavigationTarget("accounts/99999999999999999999"))
                .contains(AccountDetailView.class);
        assertThat(registry.getNavigationTarget("accounts/7/extra")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"not-a-number", "abc", "12abc", "1.5", "0x1F", "", "99999999999999999999"})
    void nonNumericOrOutOfRangeIdGetsTheNotFoundPageWithoutAnyLookup(String parameter) {
        assertThatThrownBy(() -> view.setParameter(null, parameter))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No such account");

        verify(analytics).trackError("account.viewed", "not_found", Map.of());
        verifyNoInteractions(accountService, transactionService, programService);
    }

    @Test
    void nonAsciiDigitsAreNotAnAccountId() {
        // Arabic-Indic "12", which Long.valueOf alone would turn into 12.
        assertThat(AccountDetailView.parseAccountId("\u0661\u0662")).isEmpty();
    }

    @Test
    void signedAsciiIdsThatFitALongParse() {
        assertThat(AccountDetailView.parseAccountId("7")).contains(7L);
        assertThat(AccountDetailView.parseAccountId("+7")).contains(7L);
        assertThat(AccountDetailView.parseAccountId("-1")).contains(-1L);
        assertThat(AccountDetailView.parseAccountId("9223372036854775807"))
                .contains(Long.MAX_VALUE);
        assertThat(AccountDetailView.parseAccountId("9223372036854775808")).isEmpty();
    }

    @Test
    void unknownOrAnotherHoldersAccountIdGetsTheSameNotFoundPage() {
        when(accountService.requireAccount(999999999L))
                .thenThrow(new IllegalArgumentException("No such account: 999999999"));

        assertThatThrownBy(() -> view.setParameter(null, "999999999"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No such account");

        verify(analytics)
                .trackError("account.viewed", "not_found", Map.of("accountId", "999999999"));
        verifyNoInteractions(transactionService, programService);
    }

    @Test
    void ownAccountIdShowsTheAccount() {
        AccountHolder holder = new AccountHolder("Jordan Ellis", "jordan.ellis@example.com");
        Account account =
                new Account(
                        holder,
                        "1000000004821",
                        AccountType.CHECKING,
                        "Everyday Checking",
                        BigDecimal.TEN,
                        LocalDate.of(2021, 3, 12));
        when(accountService.requireAccount(7L)).thenReturn(account);
        when(transactionService.forAccount(7L)).thenReturn(List.of());

        view.setParameter(null, "7");

        verify(analytics).track("account.viewed", Map.of("accountId", "7"));
        assertThat(view.getElement().getOuterHTML()).contains("Everyday Checking");
    }
}
