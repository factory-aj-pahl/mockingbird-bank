package com.mockingbirdbank.service;

import com.mockingbirdbank.model.Account;
import com.mockingbirdbank.model.AccountHolder;
import com.mockingbirdbank.model.AccountType;
import com.mockingbirdbank.model.ProgramParticipation;
import com.mockingbirdbank.model.ProgramType;
import com.mockingbirdbank.repository.ProgramParticipationRepository;
import com.mockingbirdbank.resilience.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evaluates the programs a customer takes part in against each program's rules:
 *
 * <ul>
 *   <li><b>Regular</b>: exactly one checking and exactly one savings account, and the savings
 *       balance is in US dollars and strictly greater than $100.00. A balance in another currency
 *       never counts; there is no conversion.
 *   <li><b>Youth Savings</b>: exactly one savings account, no checking account, and a customer
 *       under 18 on today's business date (see {@code BusinessClockConfig}). A missing or future
 *       date of birth does not pass. There is no minimum balance.
 * </ul>
 *
 * <p>Rules are evaluated every time they are asked for, and an unmet rule only produces a message:
 * nothing here changes an account, a balance or a program.
 */
@Service
@Transactional(readOnly = true)
public class ProgramService {

    /** Regular needs a savings balance strictly greater than this, in US dollars. */
    static final BigDecimal REGULAR_SAVINGS_FLOOR = new BigDecimal("100.00");

    /** Youth Savings is only for customers younger than this. */
    static final int YOUTH_AGE_LIMIT = 18;

    private final ProgramParticipationRepository participationRepository;
    private final Clock businessClock;
    private final CircuitBreaker circuitBreaker;

    public ProgramService(
            ProgramParticipationRepository participationRepository,
            Clock businessClock,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        this.participationRepository = participationRepository;
        this.businessClock = businessClock;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("programs");
    }

    /** Every program {@code holder} takes part in, each evaluated as of today's business date. */
    public List<ProgramEvaluation> evaluationsFor(AccountHolder holder) {
        List<ProgramParticipation> participations =
                protectedCall(
                        () -> {
                            List<ProgramParticipation> found =
                                    participationRepository.findByHolderIdOrderByIdAsc(
                                            holder.getId());
                            found.forEach(
                                    participation ->
                                            Hibernate.initialize(participation.getAccounts()));
                            return found;
                        });
        LocalDate today = LocalDate.now(businessClock);
        return participations.stream()
                .map(participation -> evaluate(participation, today))
                .toList();
    }

    /** The evaluated program {@code account} takes part in, if it takes part in one. */
    public Optional<ProgramEvaluation> evaluationFor(Account account) {
        return evaluationsFor(account.getHolder()).stream()
                .filter(evaluation -> evaluation.includes(account.getId()))
                .findFirst();
    }

    ProgramEvaluation evaluate(ProgramParticipation participation, LocalDate today) {
        AccountHolder holder = participation.getHolder();
        // The V2 composite foreign key already stops another customer's account from joining this
        // program; filtering again keeps such an account off screen even if that key were lost.
        List<Account> owned =
                participation.getAccounts().stream()
                        .filter(account -> AccountService.isOwnedBy(account, holder))
                        .toList();
        List<String> unmet =
                switch (participation.getProgramType()) {
                    case REGULAR -> regularUnmetRules(owned);
                    case YOUTH_SAVINGS ->
                            youthSavingsUnmetRules(owned, holder.getDateOfBirth(), today);
                };
        return new ProgramEvaluation(participation.getProgramType(), owned, unmet);
    }

    private static List<String> regularUnmetRules(List<Account> accounts) {
        List<String> unmet = new ArrayList<>();
        List<Account> savings = ofType(accounts, AccountType.SAVINGS);
        exactlyOne(ProgramType.REGULAR, "checking", ofType(accounts, AccountType.CHECKING).size())
                .ifPresent(unmet::add);
        exactlyOne(ProgramType.REGULAR, "savings", savings.size()).ifPresent(unmet::add);
        if (savings.size() == 1) {
            savingsBalanceRule(savings.get(0)).ifPresent(unmet::add);
        }
        return unmet;
    }

    private static Optional<String> savingsBalanceRule(Account savings) {
        if (!Account.USD.equals(savings.getCurrency())) {
            return Optional.of(
                    "Regular needs the savings account in US dollars, but "
                            + savings.getDisplayName()
                            + " is held in "
                            + savings.getCurrency()
                            + ". Other currencies are not converted.");
        }
        if (savings.getBalance().compareTo(REGULAR_SAVINGS_FLOOR) <= 0) {
            return Optional.of(
                    "Regular needs a savings balance over "
                            + usd(REGULAR_SAVINGS_FLOOR)
                            + ", but "
                            + savings.getDisplayName()
                            + " has "
                            + usd(savings.getBalance())
                            + ".");
        }
        return Optional.empty();
    }

    private static List<String> youthSavingsUnmetRules(
            List<Account> accounts, LocalDate dateOfBirth, LocalDate today) {
        List<String> unmet = new ArrayList<>();
        List<Account> checking = ofType(accounts, AccountType.CHECKING);
        if (!checking.isEmpty()) {
            unmet.add(
                    "Youth Savings is savings-only, but a checking account is linked: "
                            + checking.stream()
                                    .map(Account::getDisplayName)
                                    .collect(Collectors.joining(", "))
                            + ".");
        }
        exactlyOne(
                        ProgramType.YOUTH_SAVINGS,
                        "savings",
                        ofType(accounts, AccountType.SAVINGS).size())
                .ifPresent(unmet::add);
        ageRule(dateOfBirth, today).ifPresent(unmet::add);
        return unmet;
    }

    private static Optional<String> ageRule(LocalDate dateOfBirth, LocalDate today) {
        if (dateOfBirth == null) {
            return Optional.of(
                    "Youth Savings needs the customer's date of birth, but none is on file.");
        }
        if (dateOfBirth.isAfter(today)) {
            return Optional.of(
                    "Youth Savings needs a valid date of birth, but the one on file is after"
                            + " today.");
        }
        // Age in completed years, as Period counts them: a customer born on February 29 turns 18
        // on March 1 in a year without a February 29.
        int age = Period.between(dateOfBirth, today).getYears();
        if (age >= YOUTH_AGE_LIMIT) {
            return Optional.of(
                    "Youth Savings is only for customers under "
                            + YOUTH_AGE_LIMIT
                            + ", but the customer is "
                            + age
                            + ".");
        }
        return Optional.empty();
    }

    private static Optional<String> exactlyOne(ProgramType program, String kind, int count) {
        if (count == 1) {
            return Optional.empty();
        }
        return Optional.of(
                program.getDisplayName()
                        + " needs exactly one "
                        + kind
                        + " account, but "
                        + (count == 0 ? "none is" : count + " are")
                        + " linked.");
    }

    // A new formatter per call: NumberFormat is not thread-safe and this service is a singleton.
    private static String usd(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(Locale.US).format(amount);
    }

    private static List<Account> ofType(List<Account> accounts, AccountType type) {
        return accounts.stream().filter(account -> account.getAccountType() == type).toList();
    }

    private <T> T protectedCall(Supplier<T> repositoryCall) {
        try {
            return circuitBreaker.executeSupplier(repositoryCall);
        } catch (CallNotPermittedException e) {
            throw new ServiceUnavailableException("Programs are temporarily unavailable", e);
        }
    }
}
