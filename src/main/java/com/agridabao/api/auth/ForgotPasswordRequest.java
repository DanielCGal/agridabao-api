package com.agridabao.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Step 1 of recovery: the email or display name of the account to recover. */
public record ForgotPasswordRequest(
        @NotBlank @Size(max = 320) String identifier
) {
}
