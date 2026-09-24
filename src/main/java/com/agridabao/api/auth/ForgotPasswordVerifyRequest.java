package com.agridabao.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForgotPasswordVerifyRequest(
        @NotBlank @Size(max = 320) String identifier,
        @NotBlank @Size(max = 20) String code
) {
}
