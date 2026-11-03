package com.mockingbirdbank.ui.view;

import com.mockingbirdbank.model.Account;
import com.mockingbirdbank.resilience.ServiceUnavailableException;
import com.mockingbirdbank.service.AccountService;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.function.SerializableRunnable;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;

/**
 * A masked account number ("•••• 4821") with an eye button that fetches the full number from {@link
 * AccountService#revealAccountNumber} only when clicked. While masked, this component holds nothing
 * but the account ID and the masked text - the full number exists in the UI only while it is
 * actually being shown, and is dropped again on hide, after {@link #AUTO_HIDE_SECONDS}, or when the
 * component leaves the page.
 *
 * <p>Text styling (color, font size) is inherited, so callers style this component like a plain
 * {@code Span}.
 */
public class AccountNumberDisplay extends HorizontalLayout {

    static final int AUTO_HIDE_SECONDS = 30;

    private final Long accountId;
    private final String prefix;
    private final String maskedNumber;
    private final AccountService accountService;
    private final Span number;
    private final Button toggle;
    private final List<SerializableRunnable> revealListeners = new ArrayList<>();
    private boolean revealed;
    // Bumped on every show/hide so an auto-hide timer from an earlier reveal can't hide a later
    // one early.
    private int revealGeneration;

    public AccountNumberDisplay(Account account, String prefix, AccountService accountService) {
        this.accountId = account.getId();
        this.prefix = prefix;
        this.maskedNumber = account.getMaskedNumber();
        this.accountService = accountService;
        setPadding(false);
        setSpacing(false);
        setAlignItems(FlexLayout.Alignment.CENTER);
        getStyle().set("gap", "0.3rem");
        addClassName("account-number");

        number = new Span(prefix + maskedNumber);
        number.addClassName("account-number-value");

        toggle = new Button(VaadinIcon.EYE.create());
        toggle.getElement().setAttribute("theme", "tertiary-inline icon small");
        toggle.addClassName("account-number-toggle");
        toggle.getStyle()
                .set("color", "inherit")
                .set("min-width", "0")
                .set("padding", "0")
                .set("margin", "0")
                .set("cursor", "pointer");
        // Inside AccountCard this button sits within an <a>; without preventDefault and
        // stopPropagation, clicking the eye would also navigate to the account page.
        toggle.getElement()
                .addEventListener("click", click -> onToggle())
                .preventDefault()
                .stopPropagation();
        updateToggle();

        add(number, toggle);
        addDetachListener(detach -> hide());
    }

    public Long getAccountId() {
        return accountId;
    }

    public boolean isRevealed() {
        return revealed;
    }

    /** Called whenever this display switches between masked and revealed. */
    public void addRevealListener(SerializableRunnable listener) {
        revealListeners.add(listener);
    }

    /** Shows an already-fetched full number, e.g. from a bulk "show all" call. */
    public void show(String fullNumber) {
        number.setText(prefix + fullNumber);
        revealed = true;
        int generation = ++revealGeneration;
        getElement()
                .executeJs(
                        "setTimeout(() => this.$server && this.$server.autoHide($0), $1)",
                        generation,
                        AUTO_HIDE_SECONDS * 1000);
        updateToggle();
        revealListeners.forEach(SerializableRunnable::run);
    }

    public void hide() {
        if (!revealed) {
            return;
        }
        number.setText(prefix + maskedNumber);
        revealed = false;
        revealGeneration++;
        updateToggle();
        revealListeners.forEach(SerializableRunnable::run);
    }

    @ClientCallable
    void autoHide(int generation) {
        if (generation == revealGeneration) {
            hide();
        }
    }

    private void onToggle() {
        if (revealed) {
            hide();
            return;
        }
        try {
            show(accountService.revealAccountNumber(accountId));
        } catch (AccessDeniedException | ServiceUnavailableException e) {
            showRevealFailed();
        }
    }

    static void showRevealFailed() {
        Notification.show(
                "We couldn't show the full account number right now. Please try again.",
                4000,
                Notification.Position.TOP_CENTER);
    }

    private void updateToggle() {
        toggle.setIcon(revealed ? VaadinIcon.EYE_SLASH.create() : VaadinIcon.EYE.create());
        String label = revealed ? "Hide full account number" : "Show full account number";
        toggle.setAriaLabel(label);
        toggle.setTooltipText(label);
    }
}
