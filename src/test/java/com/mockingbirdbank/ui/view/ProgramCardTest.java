package com.mockingbirdbank.ui.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.mockingbirdbank.model.Account;
import com.mockingbirdbank.model.AccountType;
import com.mockingbirdbank.model.ProgramType;
import com.mockingbirdbank.service.ProgramEvaluation;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Anchor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ProgramCardTest {

    @Test
    void metProgramListsItsAccountsAsLinksWithoutUnmetRules() {
        ProgramCard card =
                new ProgramCard(
                        new ProgramEvaluation(
                                ProgramType.REGULAR,
                                List.of(
                                        account(7L, AccountType.CHECKING, "Everyday Checking"),
                                        account(9L, AccountType.SAVINGS, "High-Yield Savings")),
                                List.of()));

        assertThat(card.getElement().getAttribute("data-program")).isEqualTo("REGULAR");
        assertThat(card.getElement().getTextRecursively()).contains(ProgramCard.MET);
        assertThat(
                        descendants(card)
                                .filter(Anchor.class::isInstance)
                                .map(Anchor.class::cast)
                                .map(link -> link.getHref() + " " + link.getText()))
                .containsExactly(
                        "accounts/7 Everyday Checking · Checking",
                        "accounts/9 High-Yield Savings · Savings");
        assertThat(card.getElement().getOuterHTML()).doesNotContain("program-unmet-rules");
    }

    @Test
    void unmetProgramShowsEveryUnmetRule() {
        ProgramCard card =
                new ProgramCard(
                        new ProgramEvaluation(
                                ProgramType.REGULAR,
                                List.of(),
                                List.of("Missing checking.", "Missing savings.")));

        String text = card.getElement().getTextRecursively();
        assertThat(text)
                .contains(ProgramCard.NOT_MET)
                .contains(ProgramCard.NO_ACCOUNTS)
                .contains("Missing checking.")
                .contains("Missing savings.");
    }

    private static Account account(long id, AccountType type, String nickname) {
        Account account =
                new Account(null, "100" + id, type, nickname, BigDecimal.TEN, LocalDate.now());
        account.setId(id);
        return account;
    }

    private static Stream<Component> descendants(Component component) {
        return Stream.concat(
                Stream.of(component),
                component.getChildren().flatMap(ProgramCardTest::descendants));
    }
}
