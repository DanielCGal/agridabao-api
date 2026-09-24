package com.agridabao.api.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

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
