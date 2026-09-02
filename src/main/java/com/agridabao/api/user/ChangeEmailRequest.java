package com.agridabao.api.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Step 1 of moving an account to a new address. */
public record ChangeEmailRequest(
        @NotBlank @Email @Size(max = 320) String newEmail
) {
}
