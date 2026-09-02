package com.agridabao.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Step 2 of recovery.
 *
 * The identifier is sent again rather than the account's email, because a
 * player who started this from their display name has never been told the
 * address and the server deliberately keeps it that way.
 */
public record ForgotPasswordVerifyRequest(
        @NotBlank @Size(max = 320) String identifier,
        @NotBlank @Size(max = 20) String code
) {
}
