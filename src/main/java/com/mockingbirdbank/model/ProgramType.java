package com.mockingbirdbank.model;

/**
 * The programs a customer can take part in. The rules for each one live in {@code ProgramService},
 * which evaluates them every time a program is shown rather than storing a result.
 */
public enum ProgramType {
    /** Exactly one checking and one savings account, with a USD savings balance over $100.00. */
    REGULAR("Regular"),

    /** Exactly one savings account and no checking account, for a customer under 18. */
    YOUTH_SAVINGS("Youth Savings");

    private final String displayName;

    ProgramType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
