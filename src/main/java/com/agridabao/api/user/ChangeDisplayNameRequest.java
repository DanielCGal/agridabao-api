package com.agridabao.api.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeDisplayNameRequest(
        @NotBlank @Size(max = AccountFields.DISPLAY_NAME_MAX) String displayName
) {
}
