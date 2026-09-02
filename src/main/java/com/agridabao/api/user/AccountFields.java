package com.agridabao.api.user;

import com.agridabao.api.error.ConflictException;

import java.util.Locale;

/**
 * The rules an email address and a display name have to follow, in one place
 * because sign-up, sign-in, password recovery and the Account panel all apply
 * the same ones and had no business each having their own idea of them.
 */
public final class AccountFields {
    /** Long enough to be typed on purpose, short enough for the plank it is drawn on. */
    public static final int DISPLAY_NAME_MIN = 3;
    public static final int DISPLAY_NAME_MAX = 80;

    private AccountFields() {
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Blank becomes null, because a display name is optional and "" is not a name. */
    public static String normalizeDisplayName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /**
     * Checks a display name a player chose. Applied only to names being set from
     * now on: accounts created before display names had to be unique keep
     * whatever they have until their owner changes it.
     */
    public static void validateDisplayName(String displayName) {
        if (displayName.length() < DISPLAY_NAME_MIN) {
            throw new ConflictException(
                    "Your display name needs at least " + DISPLAY_NAME_MIN + " characters.");
        }

        if (displayName.length() > DISPLAY_NAME_MAX) {
            throw new ConflictException(
                    "Your display name can be at most " + DISPLAY_NAME_MAX + " characters.");
        }

        // Sign-in tries the typed text as an address first and as a display name
        // second, so a name shaped like an address would be shadowed by whoever
        // owns that address and could never be signed in with.
        if (displayName.indexOf('@') >= 0) {
            throw new ConflictException("Your display name cannot contain the @ sign.");
        }
    }

    /**
     * Enough of an address for its owner to recognise it, and not enough for
     * anyone else to use. Shown when a player asks to reset a password from a
     * display name, where they have proven nothing yet.
     */
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

        // A fixed run of stars rather than one per hidden character, so the
        // length of the address is not given away either.
        return head + "****" + domain;
    }
}
