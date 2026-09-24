package com.agridabao.api.auth;

public record PasswordResetTicketResponse(
        String resetToken,
        long expiresInSeconds
) {
}
