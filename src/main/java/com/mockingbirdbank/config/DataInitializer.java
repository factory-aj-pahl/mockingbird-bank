package com.mockingbirdbank.config;

import com.mockingbirdbank.model.Account;
import com.mockingbirdbank.model.AccountHolder;
import com.mockingbirdbank.model.AccountType;
import com.mockingbirdbank.model.AppUser;
import com.mockingbirdbank.model.ProgramParticipation;
import com.mockingbirdbank.model.ProgramType;
import com.mockingbirdbank.model.Transaction;
import com.mockingbirdbank.repository.AccountHolderRepository;
import com.mockingbirdbank.repository.AccountRepository;
import com.mockingbirdbank.repository.AppUserRepository;
import com.mockingbirdbank.repository.ProgramParticipationRepository;
import com.mockingbirdbank.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds two account holders, each with a matching login, accounts, sample transactions and one
 * program, so the dashboard has something real to show: Jordan Ellis (an adult with a Regular
 * program) and Riley Chen (a customer under 18 with a Youth Savings program). Runs only on an empty
 * database - safe to leave on in dev; swap for a proper migration tool before this becomes a shared
 * environment.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    // Public (not just package-visible) so the smoke test can assert
    // against the seeded login directly instead of duplicating the values -
    // keeps the seed and its test coverage from silently drifting apart.
    public static final String DEV_USERNAME = "jordan.ellis";
    public static final String YOUTH_USERNAME = "riley.chen";
    public static final String DEV_PASSWORD_ENV = "MOCKINGBIRD_DEV_PASSWORD";
    public static final String DEV_PASSWORD_DEFAULT = "mockingbird";

    /**
     * The youth customer's age on the day the database is seeded. Relative rather than a fixed date
     * of birth, so a freshly seeded demo always has a customer who is under 18.
     */
    static final int YOUTH_AGE_AT_SEED = 15;

    private static final Object[][] ADULT_TRANSACTIONS = {
        {0, "Trader Joe's", "Groceries", "-86.42"},
        {1, "Direct Deposit - Bespin Engineering", "Income", "3200.00"},
        {2, "Green Valley Utilities", "Utilities", "-142.10"},
        {4, "Transfer to Savings", "Transfer", "-500.00"},
        {6, "Blue Bottle Coffee", "Dining", "-6.75"},
        {9, "Rent - Meridian Apartments", "Housing", "-1450.00"},
        {12, "Amazon", "Shopping", "-58.19"},
        {14, "Direct Deposit - Bespin Engineering", "Income", "3200.00"}
    };

    private static final Object[][] YOUTH_TRANSACTIONS = {
        {1, "Birthday gift", "Income", "50.00"},
        {5, "Weekly allowance", "Income", "20.00"},
        {8, "Corner Bookstore", "Shopping", "-12.99"},
        {12, "Weekly allowance", "Income", "20.00"}
    };

    private final AccountHolderRepository holderRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AppUserRepository appUserRepository;
    private final ProgramParticipationRepository participationRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock businessClock;

    public DataInitializer(
            AccountHolderRepository holderRepository,
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            AppUserRepository appUserRepository,
            ProgramParticipationRepository participationRepository,
            PasswordEncoder passwordEncoder,
            Clock businessClock) {
        this.holderRepository = holderRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.appUserRepository = appUserRepository;
        this.participationRepository = participationRepository;
        this.passwordEncoder = passwordEncoder;
        this.businessClock = businessClock;
    }

    @Override
    public void run(String... args) {
        if (holderRepository.count() > 0) {
            return;
        }

        String devPassword = System.getenv().getOrDefault(DEV_PASSWORD_ENV, DEV_PASSWORD_DEFAULT);
        seedAdultHolder(devPassword);
        seedYouthHolder(devPassword);
        // Never log the password itself, even for a local dev seed - anyone who sets
        // MOCKINGBIRD_DEV_PASSWORD to a real password they reuse elsewhere shouldn't have it
        // land in plaintext log output. AGENTS.md documents the default for local dev.
        log.info(
                "Seeded DEMO logins for local/dev use only - usernames: {}, {} (see AGENTS.md for"
                        + " the password, or set it via {})",
                DEV_USERNAME,
                YOUTH_USERNAME,
                DEV_PASSWORD_ENV);
    }

    private void seedAdultHolder(String devPassword) {
        AccountHolder holder =
                holderRepository.save(
                        new AccountHolder(
                                "Jordan Ellis",
                                "jordan.ellis@example.com",
                                LocalDate.of(1988, 4, 23)));
        appUserRepository.save(
                new AppUser(DEV_USERNAME, passwordEncoder.encode(devPassword), holder));
        ProgramParticipation regular =
                participationRepository.save(new ProgramParticipation(holder, ProgramType.REGULAR));

        Account checking =
                new Account(
                        holder,
                        "1000000004821",
                        AccountType.CHECKING,
                        "Everyday Checking",
                        new BigDecimal("4318.52"),
                        LocalDate.of(2021, 3, 12));
        checking.setProgramParticipation(regular);
        Account savings =
                new Account(
                        holder,
                        "2000000007743",
                        AccountType.SAVINGS,
                        "High-Yield Savings",
                        new BigDecimal("18200.00"),
                        LocalDate.of(2022, 1, 5));
        savings.setProgramParticipation(regular);
        // Deliberately outside any program: Regular takes exactly one savings account.
        Account vacation =
                new Account(
                        holder,
                        "2000000000092",
                        AccountType.SAVINGS,
                        "Vacation Savings",
                        new BigDecimal("1800.00"),
                        LocalDate.of(2023, 6, 20));

        seedTransactions(accountRepository.save(checking), ADULT_TRANSACTIONS);
        seedTransactions(accountRepository.save(savings), ADULT_TRANSACTIONS);
        seedTransactions(accountRepository.save(vacation), ADULT_TRANSACTIONS);
    }

    private void seedYouthHolder(String devPassword) {
        LocalDate dateOfBirth = LocalDate.now(businessClock).minusYears(YOUTH_AGE_AT_SEED);
        AccountHolder holder =
                holderRepository.save(
                        new AccountHolder("Riley Chen", "riley.chen@example.com", dateOfBirth));
        appUserRepository.save(
                new AppUser(YOUTH_USERNAME, passwordEncoder.encode(devPassword), holder));
        ProgramParticipation youthSavings =
                participationRepository.save(
                        new ProgramParticipation(holder, ProgramType.YOUTH_SAVINGS));

        Account savings =
                new Account(
                        holder,
                        "2000000005317",
                        AccountType.SAVINGS,
                        "Youth Savings",
                        new BigDecimal("245.50"),
                        LocalDate.of(2024, 9, 3));
        savings.setProgramParticipation(youthSavings);
        seedTransactions(accountRepository.save(savings), YOUTH_TRANSACTIONS);
    }

    private void seedTransactions(Account account, Object[][] rows) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (Object[] row : rows) {
            int daysAgo = (int) row[0];
            transactionRepository.save(
                    new Transaction(
                            account,
                            now.minusDays(daysAgo),
                            (String) row[1],
                            (String) row[2],
                            new BigDecimal((String) row[3])));
        }
    }
}
