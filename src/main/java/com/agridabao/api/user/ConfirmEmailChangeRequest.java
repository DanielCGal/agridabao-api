package com.agridabao.api.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmEmailChangeRequest(
        @NotBlank @Size(max = 20) String code
) {
}
