package com.mockingbirdbank.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mockingbirdbank.config.BusinessClockConfig;
import com.mockingbirdbank.model.Account;
import com.mockingbirdbank.model.AccountHolder;
import com.mockingbirdbank.model.AccountType;
import com.mockingbirdbank.model.ProgramParticipation;
import com.mockingbirdbank.model.ProgramType;
import com.mockingbirdbank.repository.ProgramParticipationRepository;
import com.mockingbirdbank.resilience.ResilienceConfig;
import com.mockingbirdbank.resilience.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProgramServiceTest {

    // 02:30 UTC on May 14 is still the evening of May 13 in New York.
    private static final Instant LATE_EVENING_IN_NEW_YORK = Instant.parse("2026-05-14T02:30:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 13);

    private final ProgramParticipationRepository repository =
            mock(ProgramParticipationRepository.class);
    private final CircuitBreakerRegistry circuitBreakerRegistry =
            CircuitBreakerRegistry.of(ResilienceConfig.defaultConfig());
    private final ProgramService service =
            new ProgramService(
                    repository,
                    Clock.fixed(LATE_EVENING_IN_NEW_YORK, BusinessClockConfig.BUSINESS_ZONE),
                    circuitBreakerRegistry);

    private final AccountHolder adult = holder(1L, LocalDate.of(1988, 4, 23));
    private long nextAccountId = 10;

    @ParameterizedTest
    @CsvSource({"100.01, true", "18200.00, true", "100.00, false", "99.99, false", "0.00, false"})
    void regularNeedsAUsdSavingsBalanceStrictlyOverOneHundredDollars(
            String balance, boolean eligible) {
        Account checking = account(adult, AccountType.CHECKING, "Everyday Checking", "4318.52");
        Account savings = account(adult, AccountType.SAVINGS, "High-Yield Savings", balance);

        ProgramEvaluation evaluation =
                evaluate(participation(ProgramType.REGULAR, adult, checking, savings));

        assertThat(evaluation.eligible()).isEqualTo(eligible);
        assertThat(evaluation.accounts()).containsExactly(checking, savings);
        if (!eligible) {
            assertThat(evaluation.unmetRules())
                    .containsExactly(
                            "Regular needs a savings balance over $100.00, but High-Yield Savings"
                                    + " has $"
                                    + balance
                                    + ".");
        }
    }

    @Test
    void regularWithoutACheckingAccountSaysWhatIsMissing() {
        Account savings = account(adult, AccountType.SAVINGS, "High-Yield Savings", "500.00");

        assertThat(evaluate(participation(ProgramType.REGULAR, adult, savings)).unmetRules())
                .containsExactly("Regular needs exactly one checking account, but none is linked.");
    }

    @Test
    void regularWithoutASavingsAccountSaysWhatIsMissing() {
        Account checking = account(adult, AccountType.CHECKING, "Everyday Checking", "4318.52");

        assertThat(evaluate(participation(ProgramType.REGULAR, adult, checking)).unmetRules())
                .containsExactly("Regular needs exactly one savings account, but none is linked.");
    }

    @Test
    void regularWithNoAccountsListsBothMissingAccounts() {
        ProgramEvaluation evaluation = evaluate(participation(ProgramType.REGULAR, adult));

        assertThat(evaluation.accounts()).isEmpty();
        assertThat(evaluation.unmetRules())
                .containsExactly(
                        "Regular needs exactly one checking account, but none is linked.",
                        "Regular needs exactly one savings account, but none is linked.");
    }

    @Test
    void regularTakesExactlyOneCheckingAndOneSavingsAccount() {
        ProgramEvaluation evaluation =
                evaluate(
                        participation(
                                ProgramType.REGULAR,
                                adult,
                                account(adult, AccountType.CHECKING, "Checking A", "10.00"),
                                account(adult, AccountType.CHECKING, "Checking B", "10.00"),
                                account(adult, AccountType.SAVINGS, "Savings A", "500.00"),
                                account(adult, AccountType.SAVINGS, "Savings B", "500.00")));

        assertThat(evaluation.unmetRules())
                .containsExactly(
                        "Regular needs exactly one checking account, but 2 are linked.",
                        "Regular needs exactly one savings account, but 2 are linked.");
    }

    @Test
    void regularNeverConvertsASavingsBalanceHeldInAnotherCurrency() {
        Account checking = account(adult, AccountType.CHECKING, "Everyday Checking", "4318.52");
        Account savings = account(adult, AccountType.SAVINGS, "Euro Savings", "5000.00");
        savings.setCurrency("EUR");

        assertThat(
                        evaluate(participation(ProgramType.REGULAR, adult, checking, savings))
                                .unmetRules())
                .containsExactly(
                        "Regular needs the savings account in US dollars, but Euro Savings is held"
                                + " in EUR. Other currencies are not converted.");
    }

    @Test
    void youthSavingsIsMetTheDayBeforeThe18thBirthday() {
        AccountHolder youth = holder(2L, TODAY.plusDays(1).minusYears(18));

        ProgramEvaluation evaluation = evaluate(youthSavings(youth, "245.50"));

        assertThat(evaluation.eligible()).isTrue();
        assertThat(evaluation.unmetRules()).isEmpty();
    }

    @Test
    void youthSavingsIsNotMetOnThe18thBirthday() {
        AccountHolder youth = holder(2L, TODAY.minusYears(18));

        assertThat(evaluate(youthSavings(youth, "245.50")).unmetRules())
                .containsExactly(
                        "Youth Savings is only for customers under 18, but the customer is 18.");
    }

    @Test
    void youthSavingsIsNotMetAfterThe18thBirthday() {
        assertThat(evaluate(youthSavings(adult, "245.50")).unmetRules())
                .containsExactly(
                        "Youth Savings is only for customers under 18, but the customer is 38.");
    }

    @Test
    void youthSavingsHasNoMinimumBalance() {
        AccountHolder youth = holder(2L, TODAY.minusYears(15));

        assertThat(evaluate(youthSavings(youth, "0.00")).eligible()).isTrue();
    }

    @Test
    void youthSavingsFailsWithoutADateOfBirth() {
        AccountHolder noDateOfBirth = holder(2L, null);

        assertThat(evaluate(youthSavings(noDateOfBirth, "245.50")).unmetRules())
                .containsExactly(
                        "Youth Savings needs the customer's date of birth, but none is on file.");
    }

    @Test
    void youthSavingsFailsWithADateOfBirthAfterToday() {
        AccountHolder futureDateOfBirth = holder(2L, TODAY.plusDays(1));

        assertThat(evaluate(youthSavings(futureDateOfBirth, "245.50")).unmetRules())
                .containsExactly(
                        "Youth Savings needs a valid date of birth, but the one on file is after"
                                + " today.");
    }

    @Test
    void youthSavingsIsSavingsOnly() {
        AccountHolder youth = holder(2L, TODAY.minusYears(15));
        Account savings = account(youth, AccountType.SAVINGS, "Youth Savings", "245.50");
        Account checking = account(youth, AccountType.CHECKING, "Kid Checking", "40.00");

        assertThat(
                        evaluate(participation(ProgramType.YOUTH_SAVINGS, youth, checking, savings))
                                .unmetRules())
                .containsExactly(
                        "Youth Savings is savings-only, but a checking account is linked: Kid"
                                + " Checking.");
    }

    @Test
    void youthSavingsTakesExactlyOneSavingsAccount() {
        AccountHolder youth = holder(2L, TODAY.minusYears(15));

        assertThat(evaluate(participation(ProgramType.YOUTH_SAVINGS, youth)).unmetRules())
                .containsExactly(
                        "Youth Savings needs exactly one savings account, but none is linked.");
        assertThat(
                        evaluate(
                                        participation(
                                                ProgramType.YOUTH_SAVINGS,
                                                youth,
                                                account(youth, AccountType.SAVINGS, "A", "1.00"),
                                                account(youth, AccountType.SAVINGS, "B", "1.00")))
                                .unmetRules())
                .containsExactly(
                        "Youth Savings needs exactly one savings account, but 2 are linked.");
    }

    @Test
    void leapDayBirthdayTurns18OnMarchFirstInAYearWithoutFebruary29() {
        AccountHolder leapling = holder(2L, LocalDate.of(2008, 2, 29));
        ProgramParticipation participation = youthSavings(leapling, "245.50");

        assertThat(service.evaluate(participation, LocalDate.of(2026, 2, 28)).eligible()).isTrue();
        assertThat(service.evaluate(participation, LocalDate.of(2026, 3, 1)).eligible()).isFalse();
    }

    @Test
    void anotherCustomersAccountIsNeitherShownNorCounted() {
        AccountHolder stranger = holder(3L, LocalDate.of(1990, 1, 1));
        Account checking = account(adult, AccountType.CHECKING, "Everyday Checking", "4318.52");
        Account strangersSavings = account(stranger, AccountType.SAVINGS, "Not Yours", "9000.00");

        ProgramEvaluation evaluation =
                evaluate(participation(ProgramType.REGULAR, adult, checking, strangersSavings));

        assertThat(evaluation.accounts()).containsExactly(checking);
        assertThat(evaluation.includes(strangersSavings.getId())).isFalse();
        assertThat(evaluation.unmetRules())
                .containsExactly("Regular needs exactly one savings account, but none is linked.");
        assertThat(String.join(" ", evaluation.unmetRules())).doesNotContain("Not Yours");
    }

    @Test
    void evaluationsForUsesTodaysDateInNewYork() {
        // Turns 18 on May 14: already the date in UTC, but not yet in New York.
        AccountHolder youth = holder(2L, LocalDate.of(2008, 5, 14));
        when(repository.findByHolderIdOrderByIdAsc(2L))
                .thenReturn(List.of(youthSavings(youth, "245.50")));

        List<ProgramEvaluation> evaluations = service.evaluationsFor(youth);

        assertThat(evaluations).hasSize(1);
        assertThat(evaluations.get(0).programType()).isEqualTo(ProgramType.YOUTH_SAVINGS);
        assertThat(evaluations.get(0).eligible()).isTrue();
    }

    @Test
    void evaluationForFindsTheProgramAnAccountTakesPartIn() {
        Account checking = account(adult, AccountType.CHECKING, "Everyday Checking", "4318.52");
        Account savings = account(adult, AccountType.SAVINGS, "High-Yield Savings", "18200.00");
        Account outside = account(adult, AccountType.SAVINGS, "Vacation Savings", "1800.00");
        when(repository.findByHolderIdOrderByIdAsc(1L))
                .thenReturn(List.of(participation(ProgramType.REGULAR, adult, checking, savings)));

        assertThat(service.evaluationFor(savings))
                .hasValueSatisfying(
                        evaluation -> {
                            assertThat(evaluation.programType()).isEqualTo(ProgramType.REGULAR);
                            assertThat(evaluation.eligible()).isTrue();
                        });
        assertThat(service.evaluationFor(outside)).isEmpty();
    }

    @Test
    void evaluationsForFailsFastWhenCircuitBreakerIsOpen() {
        circuitBreakerRegistry.circuitBreaker("programs").transitionToOpenState();

        assertThatThrownBy(() -> service.evaluationsFor(adult))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    private ProgramEvaluation evaluate(ProgramParticipation participation) {
        return service.evaluate(participation, TODAY);
    }

    private ProgramParticipation youthSavings(AccountHolder youth, String balance) {
        return participation(
                ProgramType.YOUTH_SAVINGS,
                youth,
                account(youth, AccountType.SAVINGS, "Youth Savings", balance));
    }

    private static ProgramParticipation participation(
            ProgramType type, AccountHolder owner, Account... accounts) {
        ProgramParticipation participation = new ProgramParticipation(owner, type);
        for (Account account : accounts) {
            account.setProgramParticipation(participation);
            participation.getAccounts().add(account);
        }
        return participation;
    }

    private Account account(
            AccountHolder owner, AccountType type, String nickname, String balance) {
        Account account =
                new Account(
                        owner,
                        String.valueOf(nextAccountId),
                        type,
                        nickname,
                        new BigDecimal(balance),
                        LocalDate.of(2024, 1, 2));
        account.setId(nextAccountId++);
        return account;
    }

    private static AccountHolder holder(long id, LocalDate dateOfBirth) {
        AccountHolder holder =
                new AccountHolder("Customer " + id, "customer" + id + "@example.com", dateOfBirth);
        holder.setId(id);
        return holder;
    }
}
