package com.mockingbirdbank.ui.view;

import com.mockingbirdbank.analytics.AnalyticsEventService;
import com.mockingbirdbank.config.FeatureFlagService;
import com.mockingbirdbank.model.Account;
import com.mockingbirdbank.model.AccountHolder;
import com.mockingbirdbank.resilience.ServiceUnavailableException;
import com.mockingbirdbank.service.AccountService;
import com.mockingbirdbank.ui.layout.MainLayout;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import jakarta.annotation.security.PermitAll;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The landing screen: total assets up top, then one card per account. Mirrors the "Dashboard"
 * artboard in the Mockingbird Bank design mockup.
 */
@Route(value = "accounts", layout = MainLayout.class)
@RouteAlias(value = "", layout = MainLayout.class)
@PermitAll
public class DashboardView extends VerticalLayout {

    private static final NumberFormat USD = NumberFormat.getCurrencyInstance(Locale.US);

    private final FeatureFlagService featureFlagService;
    private final AccountService accountService;
    private final List<AccountNumberDisplay> numberDisplays = new ArrayList<>();
    private final Button revealAllToggle = new Button();

    public DashboardView(
            AccountService accountService,
            FeatureFlagService featureFlagService,
            AnalyticsEventService analytics) {
        this.featureFlagService = featureFlagService;
        this.accountService = accountService;
        AccountHolder holder = accountService.currentHolder();
        List<Account> accounts = accountService.accountsFor(holder);
        BigDecimal total = accountService.totalBalance(holder);
        analytics.track(
                "dashboard.viewed", Map.of("accountCount", String.valueOf(accounts.size())));

        setPadding(true);
        setSpacing(true);
        getStyle().set("background", "var(--mb-page-bg)").set("min-height", "100%");

        if (featureFlagService.showWelcomeBanner()) {
            add(welcomeBanner(holder));
        }
        // Built before the toolbar so every AccountNumberDisplay on the page already exists when
        // deciding whether the global toggle has anything to control.
        Div hero = balanceHero(holder, accounts, total);
        VerticalLayout accountsSection = accountsSection(accounts);
        if (!numberDisplays.isEmpty()) {
            add(revealAllToolbar());
        }
        add(hero, accountsSection);
    }

    private Div welcomeBanner(AccountHolder holder) {
        Span banner =
                new Span(
                        "Hi "
                                + holder.getFullName().split(" ")[0]
                                + " - you're viewing an early preview of the redesigned dashboard.");
        banner.getStyle()
                .set("display", "block")
                .set("background", "var(--mb-navy)")
                .set("color", "var(--mb-text-on-navy)")
                .set("border-radius", "12px")
                .set("padding", "0.9rem 1.5rem")
                .set("font-size", "0.9rem")
                .set("font-weight", "500");
        return new Div(banner);
    }

    private Div balanceHero(AccountHolder holder, List<Account> accounts, BigDecimal total) {
        Span label = eyebrow("Total assets");

        Span amount = new Span(USD.format(total));
        amount.getStyle()
                .set("font-family", "Georgia, serif")
                .set("font-size", "2.6rem")
                .set("font-weight", "600")
                .set("color", "var(--mb-text)");

        Span sub = new Span("Across " + accounts.size() + " accounts");
        sub.getStyle().set("color", "var(--mb-text-muted)").set("font-size", "0.85rem");

        VerticalLayout left = new VerticalLayout(label, amount, sub);
        left.setPadding(false);
        left.setSpacing(false);

        Span holderLabel = eyebrow("Account holder");
        Span holderName = new Span(holder.getFullName());
        holderName.getStyle().set("font-weight", "600").set("color", "var(--mb-text)");

        VerticalLayout right = new VerticalLayout(holderLabel, holderName);
        right.setPadding(false);
        right.setSpacing(false);
        right.setAlignItems(FlexLayout.Alignment.END);
        if (!accounts.isEmpty()) {
            right.add(primaryAccountLine(accounts.get(0)));
        }

        HorizontalLayout hero = new HorizontalLayout(left, right);
        hero.setWidthFull();
        hero.setJustifyContentMode(FlexLayout.JustifyContentMode.BETWEEN);
        hero.setAlignItems(FlexLayout.Alignment.END);
        hero.getStyle()
                .set("background", "var(--mb-surface)")
                .set("border", "1px solid var(--mb-hairline)")
                .set("border-radius", "16px")
                .set("padding", "1.75rem 2.5rem");
        return new Div(hero);
    }

    private Component primaryAccountLine(Account primary) {
        Component line = accountNumber(primary, "Primary account ");
        line.getElement()
                .getStyle()
                .set("color", "var(--mb-text-muted)")
                .set("font-size", "0.85rem");
        return line;
    }

    private VerticalLayout accountsSection(List<Account> accounts) {
        H2 heading = new H2("Your accounts");
        heading.getStyle().set("font-family", "Georgia, serif").set("margin", "0");

        FlexLayout grid = new FlexLayout();
        grid.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        grid.getStyle().set("gap", "1.25rem");
        boolean showAccountNumber = featureFlagService.showAccountNumberOnDashboard();
        accounts.forEach(
                account ->
                        grid.add(
                                new AccountCard(
                                        account,
                                        showAccountNumber ? accountNumber(account, "") : null)));

        VerticalLayout section = new VerticalLayout(heading, grid);
        section.setPadding(false);
        section.setSpacing(true);
        return section;
    }

    /**
     * An account number line: an {@link AccountNumberDisplay} with an eye toggle when reveal is
     * enabled, otherwise the plain masked text exactly as before.
     */
    private Component accountNumber(Account account, String prefix) {
        if (!featureFlagService.allowAccountNumberReveal()) {
            return new Span(prefix + account.getMaskedNumber());
        }
        AccountNumberDisplay display = new AccountNumberDisplay(account, prefix, accountService);
        display.addRevealListener(this::updateRevealAllToggle);
        numberDisplays.add(display);
        return display;
    }

    private HorizontalLayout revealAllToolbar() {
        HorizontalLayout toolbar = new HorizontalLayout(revealAllToggle());
        toolbar.setWidthFull();
        toolbar.setPadding(false);
        toolbar.setJustifyContentMode(FlexLayout.JustifyContentMode.END);
        return toolbar;
    }

    private Button revealAllToggle() {
        revealAllToggle.getElement().setAttribute("theme", "tertiary");
        revealAllToggle.addClassName("reveal-all-account-numbers");
        revealAllToggle
                .getStyle()
                .set("color", "var(--mb-positive)")
                .set("font-weight", "600")
                .set("font-size", "0.85rem");
        revealAllToggle.addClickListener(click -> toggleAll());
        updateRevealAllToggle();
        return revealAllToggle;
    }

    private boolean allRevealed() {
        return numberDisplays.stream().allMatch(AccountNumberDisplay::isRevealed);
    }

    private void toggleAll() {
        if (allRevealed()) {
            numberDisplays.forEach(AccountNumberDisplay::hide);
            return;
        }
        Map<Long, String> numbers;
        try {
            numbers = accountService.revealAccountNumbers();
        } catch (ServiceUnavailableException e) {
            AccountNumberDisplay.showRevealFailed();
            return;
        }
        numberDisplays.forEach(
                display -> {
                    String fullNumber = numbers.get(display.getAccountId());
                    if (fullNumber != null) {
                        display.show(fullNumber);
                    }
                });
    }

    private void updateRevealAllToggle() {
        boolean all = allRevealed();
        revealAllToggle.setText(all ? "Hide account numbers" : "Show account numbers");
        revealAllToggle.setIcon(all ? VaadinIcon.EYE_SLASH.create() : VaadinIcon.EYE.create());
        revealAllToggle.setAriaLabel(all ? "Hide all account numbers" : "Show all account numbers");
    }

    private Span eyebrow(String text) {
        Span span = new Span(text);
        span.getStyle()
                .set("font-size", "0.7rem")
                .set("letter-spacing", "0.08em")
                .set("text-transform", "uppercase")
                .set("color", "var(--mb-text-muted)")
                .set("font-weight", "600");
        return span;
    }
}
