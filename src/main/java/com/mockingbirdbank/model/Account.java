package com.mockingbirdbank.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "account")
@Getter
@Setter
@NoArgsConstructor
public class Account {

    /** ISO 4217 code for US dollars, the currency of every account created before currencies. */
    public static final String USD = "USD";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "holder_id")
    private AccountHolder holder;

    /** Full account number, stored as-is; only ever masked in the UI. */
    @Column(nullable = false, unique = true)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType accountType;

    /** Display name, e.g. "Vacation Savings". */
    private String nickname;

    // No default here: Hibernate always hydrates this from the DB, and the only
    // in-app constructor always sets it explicitly, so a default would be dead code.
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal balance;

    /** ISO 4217 code of the currency {@code balance} is held in. */
    @Column(nullable = false, length = 3)
    private String currency = USD;

    @Column(nullable = false)
    private LocalDate openedAt;

    /** The one program this account takes part in, or {@code null} for none. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_participation_id")
    private ProgramParticipation programParticipation;

    // Without this, iterating several accounts and touching each one's lazy
    // transactions collection issues one SELECT per account (classic N+1).
    // BatchSize lets Hibernate fetch up to 20 sibling accounts' transactions
    // in a single "WHERE account_id IN (...)" query instead.
    // See AccountTransactionQueryPerformanceTest for the regression guard.
    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 20)
    private List<Transaction> transactions = new ArrayList<>();

    public Account(
            AccountHolder holder,
            String accountNumber,
            AccountType accountType,
            String nickname,
            BigDecimal balance,
            LocalDate openedAt) {
        this.holder = holder;
        this.accountNumber = accountNumber;
        this.accountType = accountType;
        this.nickname = nickname;
        this.balance = balance;
        this.openedAt = openedAt;
    }

    /** Masked for display: "•••• 4821". */
    public String getMaskedNumber() {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "••••";
        }
        return "•••• " + accountNumber.substring(accountNumber.length() - 4);
    }

    public String getDisplayName() {
        return nickname != null && !nickname.isBlank() ? nickname : accountType.name();
    }
}
