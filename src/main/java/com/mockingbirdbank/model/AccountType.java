package com.mockingbirdbank.model;

public enum AccountType {
    CHECKING("Checking"),
    SAVINGS("Savings");

    private final String displayName;

    AccountType(String displayName) {
        this.displayName = displayName;
    }

    /** The label the UI shows for this kind of account, e.g. on account and program cards. */
    public String getDisplayName() {
        return displayName;
    }
}
