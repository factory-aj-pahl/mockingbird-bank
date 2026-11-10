package com.mockingbirdbank.ui.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.mockingbirdbank.analytics.AnalyticsEventService;
import com.mockingbirdbank.config.FeatureFlagService;
import com.mockingbirdbank.config.FeatureFlagsProperties;
import com.mockingbirdbank.model.Account;
import com.mockingbirdbank.model.AccountHolder;
import com.mockingbirdbank.model.AccountType;
import com.mockingbirdbank.model.ProgramType;
import com.mockingbirdbank.resilience.ServiceUnavailableException;
import com.mockingbirdbank.service.AccountService;
import com.mockingbirdbank.service.ProgramEvaluation;
import com.mockingbirdbank.service.ProgramService;
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
    private final ProgramService programService = mock(ProgramService.class);
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

    @Test
    void programsFlagOffNeitherEvaluatesNorShowsPrograms() {
        DashboardView view = dashboard(false, 3);

        verifyNoInteractions(programService);
        assertThat(withClass(view, "programs-section")).isEmpty();
        assertThat(withClass(view, "program-notice")).isEmpty();
    }

    @Test
    void programsFlagOnShowsEveryProgramAndANoticeForUnmetRules() {
        Account savings = account(AccountType.SAVINGS, "Youth Savings");
        when(programService.evaluationsFor(holder))
                .thenReturn(
                        List.of(
                                new ProgramEvaluation(
                                        ProgramType.REGULAR,
                                        List.of(
                                                account(AccountType.CHECKING, "Everyday Checking"),
                                                account(AccountType.SAVINGS, "High-Yield Savings")),
                                        List.of()),
                                new ProgramEvaluation(
                                        ProgramType.YOUTH_SAVINGS,
                                        List.of(savings),
                                        List.of("Youth Savings is only for customers under 18."))));

        DashboardView view = programsDashboard();

        assertThat(withClass(view, "program-card")).hasSize(2);
        assertThat(texts(view, "program-name")).containsExactly("Regular", "Youth Savings");
        assertThat(texts(view, "program-status"))
                .containsExactly(ProgramCard.MET, ProgramCard.NOT_MET);
        assertThat(texts(view, "program-unmet-rule"))
                .containsExactly("Youth Savings is only for customers under 18.");
        assertThat(texts(view, "program-notice")).containsExactly(ProgramCard.UNMET_NOTICE);
    }

    @Test
    void programsFlagOnWithEveryRuleMetShowsNoNotice() {
        when(programService.evaluationsFor(holder))
                .thenReturn(
                        List.of(
                                new ProgramEvaluation(
                                        ProgramType.YOUTH_SAVINGS,
                                        List.of(account(AccountType.SAVINGS, "Youth Savings")),
                                        List.of())));

        DashboardView view = programsDashboard();

        assertThat(texts(view, "program-status")).containsExactly(ProgramCard.MET);
        assertThat(withClass(view, "program-notice")).isEmpty();
    }

    @Test
    void programsFlagOnWithoutProgramsSaysSo() {
        when(programService.evaluationsFor(holder)).thenReturn(List.of());

        assertThat(texts(programsDashboard(), "programs-none"))
                .containsExactly(ProgramCard.NO_PROGRAMS);
    }

    @Test
    void programsUnavailableStillShowsTheAccounts() {
        when(programService.evaluationsFor(holder))
                .thenThrow(new ServiceUnavailableException("down", null));

        DashboardView view = programsDashboard();

        assertThat(texts(view, "programs-unavailable")).containsExactly(ProgramCard.UNAVAILABLE);
        assertThat(descendants(view).filter(AccountCard.class::isInstance)).hasSize(2);
    }

    private DashboardView dashboard(boolean showAccountCount, int accountCount) {
        FeatureFlagsProperties properties = new FeatureFlagsProperties();
        properties.setShowAccountCount(showAccountCount);
        return dashboard(properties, accountCount);
    }

    private DashboardView programsDashboard() {
        FeatureFlagsProperties properties = new FeatureFlagsProperties();
        properties.setShowAccountPrograms(true);
        return dashboard(properties, 2);
    }

    private DashboardView dashboard(FeatureFlagsProperties properties, int accountCount) {
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
        return new DashboardView(
                accountService,
                new FeatureFlagService(properties),
                mock(AnalyticsEventService.class),
                programService);
    }

    private Account account(AccountType type, String nickname) {
        return new Account(
                holder, "2000000007743", type, nickname, BigDecimal.TEN, LocalDate.now());
    }

    private static List<String> accountCountLines(Component root) {
        return descendants(root)
                .filter(Span.class::isInstance)
                .filter(c -> c.getClassNames().contains("account-count-summary"))
                .map(c -> ((Span) c).getText())
                .toList();
    }

    private static List<Component> withClass(Component root, String className) {
        return descendants(root)
                .filter(c -> c.getElement().getClassList().contains(className))
                .toList();
    }

    private static List<String> texts(Component root, String className) {
        return withClass(root, className).stream()
                .map(c -> c.getElement().getTextRecursively())
                .toList();
    }

    private static Stream<Component> descendants(Component component) {
        return Stream.concat(
                Stream.of(component),
                component.getChildren().flatMap(DashboardViewTest::descendants));
    }
}
