package com.agridabao.api.user;

import com.agridabao.api.error.ConflictException;

import java.util.Locale;

public final class AccountFields {
    public static final int DISPLAY_NAME_MIN = 3;
    public static final int DISPLAY_NAME_MAX = 80;

    private AccountFields() {
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizeDisplayName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public static void validateDisplayName(String displayName) {
        if (displayName.length() < DISPLAY_NAME_MIN) {
            throw new ConflictException(
                    "Your display name needs at least " + DISPLAY_NAME_MIN + " characters.");
        }

        if (displayName.length() > DISPLAY_NAME_MAX) {
            throw new ConflictException(
                    "Your display name can be at most " + DISPLAY_NAME_MAX + " characters.");
        }

        if (displayName.indexOf('@') >= 0) {
            throw new ConflictException("Your display name cannot contain the @ sign.");
        }
    }

    public static String mask(String email) {
        if (email == null || email.isBlank()) {
            return "your email";
        }

        int at = email.indexOf('@');
        if (at <= 0) {
            return "your email";
        }

        String local = email.substring(0, at);
        String domain = email.substring(at);
        String head = local.length() >= 2 ? local.substring(0, 2) : local.substring(0, 1);

        return head + "****" + domain;
    }
}
