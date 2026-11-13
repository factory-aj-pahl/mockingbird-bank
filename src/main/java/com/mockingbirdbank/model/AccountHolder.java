package com.mockingbirdbank.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "account_holder")
@Getter
@Setter
@NoArgsConstructor
public class AccountHolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    /**
     * {@code null} when unknown, as for every customer created before this column existed. Rules
     * that depend on age, such as Youth Savings, treat a missing date of birth as not met.
     */
    private LocalDate dateOfBirth;

    @OneToMany(mappedBy = "holder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Account> accounts = new ArrayList<>();

    public AccountHolder(String fullName, String email) {
        this.fullName = fullName;
        this.email = email;
    }

    public AccountHolder(String fullName, String email, LocalDate dateOfBirth) {
        this(fullName, email);
        this.dateOfBirth = dateOfBirth;
    }
}
