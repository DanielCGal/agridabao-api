package com.agridabao.api.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Step 2 of moving an account to a new address.
 *
 * Only the code is sent. Which address is being moved to is read from the
 * pending record on the server, so a client cannot confirm one address with a
 * code that was emailed to another.
 */
public record ConfirmEmailChangeRequest(
        @NotBlank @Size(max = 20) String code
) {
}
