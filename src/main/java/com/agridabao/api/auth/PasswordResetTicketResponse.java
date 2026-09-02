package com.agridabao.api.auth;

/**
 * Handed out once the emailed reset code checks out. It is not an access token
 * and the API refuses it as one; its only use is the reset call that follows.
 */
public record PasswordResetTicketResponse(
        String resetToken,
        long expiresInSeconds
) {
}
