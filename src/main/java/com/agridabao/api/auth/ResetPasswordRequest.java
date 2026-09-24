package com.agridabao.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank String resetToken,
        @NotBlank @Size(min = 8, max = 72) String newPassword,
        @NotBlank String confirmPassword
) {
}
