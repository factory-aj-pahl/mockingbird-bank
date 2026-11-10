package com.mockingbirdbank.ui.view;

import com.mockingbirdbank.model.Account;
import com.mockingbirdbank.service.ProgramEvaluation;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import java.util.List;

/**
 * One program on the dashboard or an account page: its name, whether its rules are met, the
 * accounts taking part (each linking to its account page), and a message for every unmet rule.
 * Read-only, like the rest of the app: nothing here changes an account or a program.
 */
public class ProgramCard extends Div {

    static final String MET = "Requirements met";
    static final String NOT_MET = "Requirements not met";
    static final String NO_ACCOUNTS = "No accounts are linked to this program.";
    static final String NO_PROGRAMS = "You're not part of any programs.";
    static final String UNAVAILABLE = "Programs can't be shown right now. Please try again later.";
    static final String UNMET_NOTICE =
            "Some of your program requirements aren't met. The details are under Your programs"
                    + " below. Your accounts and balances are not affected.";

    public ProgramCard(ProgramEvaluation evaluation) {
        addClassName("program-card");
        getElement().setAttribute("data-program", evaluation.programType().name());
        getStyle()
                .set("background", "var(--mb-surface)")
                .set("border", "1px solid var(--mb-hairline)")
                .set("border-radius", "12px")
                .set("padding", "1.4rem 1.5rem")
                .set("width", "360px")
                .set("max-width", "100%")
                .set("box-sizing", "border-box")
                .set("box-shadow", "0 1px 2px var(--mb-shadow)");

        Span name = new Span(evaluation.programType().getDisplayName());
        name.addClassName("program-name");
        name.getStyle().set("font-weight", "600").set("color", "var(--mb-text)");

        HorizontalLayout header = new HorizontalLayout(name, statusPill(evaluation.eligible()));
        header.setWidthFull();
        header.setJustifyContentMode(FlexLayout.JustifyContentMode.BETWEEN);
        header.setAlignItems(FlexLayout.Alignment.CENTER);

        add(header, accountsList(evaluation.accounts()));
        if (!evaluation.eligible()) {
            add(unmetRules(evaluation.unmetRules()));
        }
    }

    /**
     * A page-level notice that at least one program has unmet rules, so it is seen without
     * scrolling to the programs themselves.
     */
    static Div unmetNotice() {
        Div notice = new Div(new Span(UNMET_NOTICE));
        notice.addClassName("program-notice");
        notice.getElement().setAttribute("role", "status");
        notice.getStyle()
                .set("background", "var(--mb-savings-bg)")
                .set("color", "var(--mb-savings)")
                .set("border", "1px solid var(--mb-savings)")
                .set("border-radius", "12px")
                .set("padding", "0.9rem 1.5rem")
                .set("font-size", "0.9rem")
                .set("font-weight", "500")
                .set("box-sizing", "border-box")
                .set("width", "100%");
        return notice;
    }

    /** A muted one-line message in place of a program, e.g. when there is none to show. */
    static Span message(String text, String className) {
        Span line = new Span(text);
        line.addClassName(className);
        line.getStyle().set("color", "var(--mb-text-muted)").set("font-size", "0.9rem");
        return line;
    }

    private static Span statusPill(boolean eligible) {
        Span status = new Span(eligible ? MET : NOT_MET);
        status.addClassName("program-status");
        status.getStyle()
                .set("font-size", "0.68rem")
                .set("font-weight", "600")
                .set("letter-spacing", "0.04em")
                .set("text-transform", "uppercase")
                .set("padding", "3px 9px")
                .set("border-radius", "999px")
                .set("white-space", "nowrap")
                .set("color", eligible ? "var(--mb-positive)" : "var(--mb-savings)")
                .set("background", eligible ? "var(--mb-positive-bg)" : "var(--mb-savings-bg)");
        return status;
    }

    private static Component accountsList(List<Account> accounts) {
        if (accounts.isEmpty()) {
            Span none = new Span(NO_ACCOUNTS);
            none.addClassName("program-no-accounts");
            none.getStyle().set("color", "var(--mb-text-muted)").set("font-size", "0.85rem");
            return none;
        }
        VerticalLayout list = new VerticalLayout();
        list.addClassName("program-accounts");
        list.setPadding(false);
        list.setSpacing(false);
        list.getStyle().set("gap", "0.35rem").set("margin-top", "0.75rem");
        for (Account account : accounts) {
            Anchor link =
                    new Anchor(
                            "accounts/" + account.getId(),
                            account.getDisplayName()
                                    + " · "
                                    + account.getAccountType().getDisplayName());
            link.addClassName("program-account");
            link.getStyle()
                    .set("color", "var(--mb-positive)")
                    .set("font-size", "0.85rem")
                    .set("font-weight", "500")
                    .set("text-decoration", "none");
            list.add(link);
        }
        return list;
    }

    private static Div unmetRules(List<String> rules) {
        Span heading = new Span("Unmet requirements");
        heading.getStyle()
                .set("font-size", "0.7rem")
                .set("letter-spacing", "0.06em")
                .set("text-transform", "uppercase")
                .set("font-weight", "600");
        UnorderedList list = new UnorderedList();
        list.getStyle().set("margin", "0.35rem 0 0").set("padding-left", "1.1rem");
        for (String rule : rules) {
            ListItem item = new ListItem(rule);
            item.addClassName("program-unmet-rule");
            list.add(item);
        }
        Div box = new Div(heading, list);
        box.addClassName("program-unmet-rules");
        box.getStyle()
                .set("margin-top", "0.9rem")
                .set("padding", "0.75rem 0.9rem")
                .set("border-radius", "8px")
                .set("background", "var(--mb-savings-bg)")
                .set("color", "var(--mb-savings)")
                .set("font-size", "0.85rem");
        return box;
    }
}
