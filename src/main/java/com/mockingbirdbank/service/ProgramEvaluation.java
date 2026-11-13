package com.mockingbirdbank.service;

import com.mockingbirdbank.model.Account;
import com.mockingbirdbank.model.ProgramType;
import java.util.List;
import java.util.Objects;

/**
 * One program participation checked against its program's rules: the accounts taking part, and a
 * customer-facing message for every rule that is not met. No messages means the rules are met.
 *
 * @param accounts the participating accounts owned by the participation's customer; an account of
 *     another customer is left out and counts toward no rule
 */
public record ProgramEvaluation(
        ProgramType programType, List<Account> accounts, List<String> unmetRules) {

    public ProgramEvaluation {
        accounts = List.copyOf(accounts);
        unmetRules = List.copyOf(unmetRules);
    }

    public boolean eligible() {
        return unmetRules.isEmpty();
    }

    public boolean includes(Long accountId) {
        return accounts.stream().anyMatch(account -> Objects.equals(account.getId(), accountId));
    }
}
