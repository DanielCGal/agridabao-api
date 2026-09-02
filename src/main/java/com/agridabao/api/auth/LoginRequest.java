package com.agridabao.api.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Sign-in credentials.
 *
 * The first field is whatever the player typed into the one box on the login
 * board - an email address or their display name - so it carries no {@code @Email}
 * rule. {@code email} is the older name for the same thing and is still accepted,
 * because a phone running a build from before this change sends only that.
 */
public record LoginRequest(
        @Size(max = 320) String identifier,
        @Size(max = 320) String email,
        @NotBlank String password
) {
    public String loginIdentifier() {
        return identifier != null && !identifier.isBlank() ? identifier : email;
    }

    @AssertTrue(message = "Enter your email or display name.")
    public boolean isIdentifierPresent() {
        String value = loginIdentifier();
        return value != null && !value.isBlank();
    }
}
