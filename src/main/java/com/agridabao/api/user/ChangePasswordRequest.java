package com.agridabao.api.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Changing a password from inside the game, where the current one is known. */
public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8, max = 72) String newPassword,
        @NotBlank String confirmPassword
) {
}
