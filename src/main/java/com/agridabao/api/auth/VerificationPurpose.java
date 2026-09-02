package com.agridabao.api.auth;

public enum VerificationPurpose {
    SIGNUP,
    LOGIN,
    /** Recovering an account whose password the player has forgotten. */
    PASSWORD_RESET,
    /**
     * Moving an account to a new address. The code goes to the NEW address, so
     * the record's email column is the one being moved to and its user_id is
     * the account doing the moving.
     */
    EMAIL_CHANGE
}
