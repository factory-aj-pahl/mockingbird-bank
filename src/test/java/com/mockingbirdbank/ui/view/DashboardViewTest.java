package com.mockingbirdbank.ui.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.mockingbirdbank.analytics.AnalyticsEventService;
import com.mockingbirdbank.config.FeatureFlagService;
import com.mockingbirdbank.config.FeatureFlagsProperties;
import com.mockingbirdbank.model.Account;
import com.mockingbirdbank.model.AccountHolder;
import com.mockingbirdbank.model.AccountType;
import com.mockingbirdbank.service.AccountService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DashboardViewTest {

    private final AccountService accountService = mock(AccountService.class);
    private final AccountHolder holder =
            new AccountHolder("Jordan Ellis", "jordan.ellis@example.com");

    @Test
    void accountCountSummaryUsesSingularForOneAccount() {
        assertThat(DashboardView.accountCountSummary(1)).isEqualTo("You have 1 account");
    }

    @Test
    void accountCountSummaryUsesPluralOtherwise() {
        assertThat(DashboardView.accountCountSummary(3)).isEqualTo("You have 3 accounts");
        assertThat(DashboardView.accountCountSummary(0)).isEqualTo("You have 0 accounts");
    }

    @Test
    void flagOnShowsCountOfAlreadyLoadedAccounts() {
        DashboardView view = dashboard(true, 3);

        assertThat(accountCountLines(view)).containsExactly("You have 3 accounts");
        verify(accountService).currentHolder();
        verify(accountService).accountsFor(holder);
        verify(accountService).totalBalance(holder);
        verifyNoMoreInteractions(accountService);
    }

    @Test
    void flagOnUsesSingularForOneAccount() {
        assertThat(accountCountLines(dashboard(true, 1))).containsExactly("You have 1 account");
    }

    @Test
    void flagOffLeavesDashboardUnchanged() {
        DashboardView view = dashboard(false, 3);

        assertThat(accountCountLines(view)).isEmpty();
        assertThat(view.getElement().getOuterHTML()).doesNotContain("You have");
    }

    private DashboardView dashboard(boolean showAccountCount, int accountCount) {
        List<Account> accounts =
                IntStream.range(0, accountCount)
                        .mapToObj(
                                i ->
                                        new Account(
                                                holder,
                                                "100000000482" + i,
                                                AccountType.CHECKING,
                                                "Account " + i,
                                                BigDecimal.TEN,
                                                LocalDate.now()))
                        .toList();
        when(accountService.currentHolder()).thenReturn(holder);
        when(accountService.accountsFor(holder)).thenReturn(accounts);
        when(accountService.totalBalance(holder)).thenReturn(BigDecimal.TEN);
        FeatureFlagsProperties properties = new FeatureFlagsProperties();
        properties.setShowAccountCount(showAccountCount);
        return new DashboardView(
                accountService,
                new FeatureFlagService(properties),
                mock(AnalyticsEventService.class));
    }

    private static List<String> accountCountLines(Component root) {
        return descendants(root)
                .filter(Span.class::isInstance)
                .filter(c -> c.getClassNames().contains("account-count-summary"))
                .map(c -> ((Span) c).getText())
                .toList();
    }

    private static Stream<Component> descendants(Component component) {
        return Stream.concat(
                Stream.of(component),
                component.getChildren().flatMap(DashboardViewTest::descendants));
    }
}
