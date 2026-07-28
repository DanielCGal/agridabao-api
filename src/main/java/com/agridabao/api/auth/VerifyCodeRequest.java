package com.agridabao.api.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyCodeRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 20) String code
) {
}
