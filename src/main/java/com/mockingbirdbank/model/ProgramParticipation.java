package com.mockingbirdbank.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/**
 * One customer's participation in a program, together with the accounts taking part in it. Each
 * account links to at most one participation (its {@code programParticipation} field), and the
 * database rejects a link to a participation that belongs to a different customer than the account
 * (see {@code V2__account_programs.sql}). Whether the participation meets its program's rules is
 * never stored here: {@code ProgramService} evaluates it each time it is shown.
 */
@Entity
@Table(name = "program_participation")
@Getter
@Setter
@NoArgsConstructor
public class ProgramParticipation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "holder_id")
    private AccountHolder holder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProgramType programType;

    // Several participations' accounts are loaded together when a holder's programs are shown;
    // BatchSize keeps that to one query for all of them, as with Account.transactions.
    @OneToMany(mappedBy = "programParticipation")
    @OrderBy("accountType ASC, id ASC")
    @BatchSize(size = 20)
    private List<Account> accounts = new ArrayList<>();

    public ProgramParticipation(AccountHolder holder, ProgramType programType) {
        this.holder = holder;
        this.programType = programType;
    }
}
