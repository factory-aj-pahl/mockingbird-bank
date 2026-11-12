package com.mockingbirdbank.ui.view;

import com.mockingbirdbank.analytics.AnalyticsEventService;
import com.mockingbirdbank.config.FeatureFlagService;
import com.mockingbirdbank.model.Account;
import com.mockingbirdbank.model.Transaction;
import com.mockingbirdbank.service.AccountService;
import com.mockingbirdbank.service.ProgramService;
import com.mockingbirdbank.service.TransactionService;
import com.mockingbirdbank.ui.layout.MainLayout;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.NotFoundException;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameterRegex;
import jakarta.annotation.security.PermitAll;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The transaction history for one account, at /accounts/{id}. Mirrors the "Account Detail" artboard
 * in the design mockup.
 *
 * <p>The ID arrives as a {@code String} and is parsed here. With a {@code Long} URL parameter, a
 * non-numeric ID such as {@code /accounts/abc} would match no route, and {@code
 * VaadinSecurityConfigurer} denies a path that matches no route with a 403 even for a signed-in
 * user. Parsing it here gives such an ID the same not-found page as an unknown numeric one.
 */
@Route(value = "accounts", layout = MainLayout.class)
@PermitAll
public class AccountDetailView extends VerticalLayout implements HasUrlParameter<String> {

    private static final NumberFormat USD = NumberFormat.getCurrencyInstance(Locale.US);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    // What a Long URL parameter accepts. Long.valueOf alone would also accept non-ASCII digits,
    // such as Arabic-Indic ones.
    private static final Pattern ACCOUNT_ID = Pattern.compile(RouteParameterRegex.LONG);

    private final AccountService accountService;
    private final TransactionService transactionService;
    private final AnalyticsEventService analytics;
    private final FeatureFlagService featureFlagService;
    private final transient ProgramService programService;

    public AccountDetailView(
            AccountService accountService,
            TransactionService transactionService,
            AnalyticsEventService analytics,
            FeatureFlagService featureFlagService,
            ProgramService programService) {
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.analytics = analytics;
        this.featureFlagService = featureFlagService;
        this.programService = programService;
        setPadding(true);
        setSpacing(true);
        getStyle().set("background", "var(--mb-page-bg)").set("min-height", "100%");
    }

    @Override
    public void setParameter(BeforeEvent event, String parameter) {
        Optional<Long> parsed = parseAccountId(parameter);
        if (parsed.isEmpty()) {
            // The raw path segment is user input, so it is not written to the event log.
            throw notFound(Map.of());
        }
        long accountId = parsed.get();
        Account account;
        try {
            account = accountService.requireAccount(accountId);
        } catch (IllegalArgumentException e) {
            throw notFound(Map.of("accountId", String.valueOf(accountId)));
        }
        analytics.track("account.viewed", Map.of("accountId", String.valueOf(accountId)));

        removeAll();
        add(backLink());
        add(accountHeader(account));
        if (featureFlagService.showAccountPrograms()) {
            add(programSection(account));
        }
        add(transactionTable(transactionService.forAccount(accountId)));
    }

    /** The account ID in {@code parameter}, or empty if it is not a number that fits a long. */
    static Optional<Long> parseAccountId(String parameter) {
        if (!ACCOUNT_ID.matcher(parameter).matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.valueOf(parameter));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private NotFoundException notFound(Map<String, String> attributes) {
        analytics.trackError("account.viewed", "not_found", attributes);
        return new NotFoundException("No such account");
    }

    private VerticalLayout programSection(Account account) {
        Span label = new Span("Program");
        label.getStyle()
                .set("font-size", "0.7rem")
                .set("text-transform", "uppercase")
                .set("letter-spacing", "0.06em")
                .set("color", "var(--mb-text-muted)")
                .set("font-weight", "600");
        VerticalLayout section = new VerticalLayout(label);
        section.addClassName("account-program");
        section.setPadding(false);
        section.setSpacing(false);
        section.getStyle().set("gap", "0.5rem");
        section.add(
                ProgramLookup.orUnavailable(analytics, () -> programContent(account))
                        .orElseGet(
                                () ->
                                        ProgramCard.message(
                                                ProgramCard.UNAVAILABLE, "programs-unavailable")));
        return section;
    }

    private Component programContent(Account account) {
        return programService
                .evaluationFor(account)
                .<Component>map(ProgramCard::new)
                .orElseGet(
                        () ->
                                ProgramCard.message(
                                        "This account isn't part of a program.", "program-none"));
    }

    private Anchor backLink() {
        Anchor back = new Anchor("accounts", "← Back to accounts");
        back.getStyle()
                .set("color", "var(--mb-positive)")
                .set("font-weight", "600")
                .set("font-size", "0.85rem")
                .set("text-decoration", "none");
        return back;
    }

    private HorizontalLayout accountHeader(Account account) {
        Span name = new Span(account.getDisplayName());
        name.getStyle()
                .set("font-family", "Georgia, serif")
                .set("font-size", "1.4rem")
                .set("font-weight", "600")
                .set("color", "var(--mb-text)");
        Component number =
                featureFlagService.allowAccountNumberReveal()
                        ? new AccountNumberDisplay(account, "Account ", accountService)
                        : new Span("Account " + account.getMaskedNumber());
        HorizontalLayout meta =
                new HorizontalLayout(number, new Span("· Opened " + account.getOpenedAt()));
        meta.setPadding(false);
        meta.setSpacing(false);
        meta.setAlignItems(FlexLayout.Alignment.CENTER);
        meta.getStyle()
                .set("gap", "0.35rem")
                .set("color", "var(--mb-text-muted)")
                .set("font-size", "0.85rem");

        VerticalLayout left = new VerticalLayout(name, meta);
        left.setPadding(false);
        left.setSpacing(false);

        Span label = new Span("Current balance");
        label.getStyle()
                .set("font-size", "0.7rem")
                .set("text-transform", "uppercase")
                .set("letter-spacing", "0.06em")
                .set("color", "var(--mb-text-muted)")
                .set("font-weight", "600");
        Span balance = new Span(USD.format(account.getBalance()));
        balance.getStyle()
                .set("font-family", "Georgia, serif")
                .set("font-size", "2rem")
                .set("font-weight", "600")
                .set("color", "var(--mb-text)");

        VerticalLayout right = new VerticalLayout(label, balance);
        right.setPadding(false);
        right.setSpacing(false);
        right.setAlignItems(FlexLayout.Alignment.END);

        HorizontalLayout header = new HorizontalLayout(left, right);
        header.setWidthFull();
        header.setJustifyContentMode(FlexLayout.JustifyContentMode.BETWEEN);
        header.setAlignItems(FlexLayout.Alignment.CENTER);
        header.getStyle()
                .set("background", "var(--mb-surface)")
                .set("border", "1px solid var(--mb-hairline)")
                .set("border-radius", "16px")
                .set("padding", "1.5rem 2rem");
        return header;
    }

    private Grid<Transaction> transactionTable(List<Transaction> transactions) {
        Grid<Transaction> grid = new Grid<>();
        grid.setItems(transactions);
        grid.setAllRowsVisible(true);
        grid.addColumn(tx -> tx.getPostedAt().format(DATE_FMT))
                .setHeader("Date")
                .setAutoWidth(true);
        grid.addColumn(Transaction::getDescription).setHeader("Description").setFlexGrow(1);
        grid.addColumn(Transaction::getCategory).setHeader("Category").setAutoWidth(true);
        grid.addComponentColumn(
                        tx -> {
                            Span amount = new Span(USD.format(tx.getAmount()));
                            amount.getStyle()
                                    .set("font-weight", "600")
                                    .set(
                                            "color",
                                            tx.isCredit()
                                                    ? "var(--mb-positive)"
                                                    : "var(--mb-text)");
                            return amount;
                        })
                .setHeader("Amount")
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);
        grid.getStyle().set("border", "1px solid var(--mb-hairline)").set("border-radius", "16px");
        grid.setWidthFull();
        return grid;
    }
}
