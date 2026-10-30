package com.mockingbirdbank.ui.view;

import com.mockingbirdbank.model.Account;
import com.mockingbirdbank.model.AccountType;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * One clickable account tile on the dashboard; the whole card links to that account's transaction
 * history.
 */
public class AccountCard extends Anchor {

    private static final NumberFormat USD = NumberFormat.getCurrencyInstance(Locale.US);

    /**
     * @param accountNumber the account number line to show, or {@code null} to hide it entirely
     *     rather than leaving a blank line (the {@code
     *     mockingbird.feature-flags.show-account-number-on-dashboard} flag, see
     *     FeatureFlagService). Either an {@link AccountNumberDisplay} or a plain masked {@link
     *     Span}, depending on whether account number reveal is enabled.
     */
    public AccountCard(Account account, Component accountNumber) {
        super("accounts/" + account.getId(), "");
        getStyle()
                .set("display", "block")
                .set("text-decoration", "none")
                .set("color", "inherit")
                .set("background", "var(--mb-surface)")
                .set("border", "1px solid var(--mb-hairline)")
                .set("border-radius", "12px")
                .set("padding", "1.4rem 1.5rem")
                .set("width", "280px")
                .set("box-shadow", "0 1px 2px var(--mb-shadow)");

        Span name = new Span(account.getDisplayName());
        name.getStyle().set("font-weight", "600").set("color", "var(--mb-text)");

        boolean isChecking = account.getAccountType() == AccountType.CHECKING;
        Span tag = new Span(isChecking ? "Checking" : "Savings");
        tag.getStyle()
                .set("font-size", "0.68rem")
                .set("font-weight", "600")
                .set("letter-spacing", "0.04em")
                .set("text-transform", "uppercase")
                .set("padding", "3px 9px")
                .set("border-radius", "999px")
                .set("color", isChecking ? "var(--mb-positive)" : "var(--mb-savings)")
                .set("background", isChecking ? "var(--mb-positive-bg)" : "var(--mb-savings-bg)");

        HorizontalLayout header = new HorizontalLayout(name, tag);
        header.setWidthFull();
        header.setJustifyContentMode(FlexLayout.JustifyContentMode.BETWEEN);
        header.setAlignItems(FlexLayout.Alignment.CENTER);

        Span balance = new Span(USD.format(account.getBalance()));
        balance.getStyle()
                .set("font-family", "Georgia, serif")
                .set("font-size", "1.6rem")
                .set("font-weight", "600")
                .set("color", "var(--mb-text)")
                .set("margin-top", "0.75rem");

        Span link = new Span("View transactions →");
        link.getStyle()
                .set("color", "var(--mb-positive)")
                .set("font-size", "0.8rem")
                .set("font-weight", "500")
                .set("margin-top", "0.85rem");

        VerticalLayout body = new VerticalLayout(header);
        body.setPadding(false);
        body.setSpacing(false);
        if (accountNumber != null) {
            accountNumber
                    .getElement()
                    .getStyle()
                    .set("color", "var(--mb-text-muted)")
                    .set("font-size", "0.8rem");
            body.add(accountNumber);
        }
        body.add(balance, link);
        add(body);
    }
}
