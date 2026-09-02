package com.agridabao.api.auth;

/**
 * Response for the "request a code" step. {@code devCode} is populated only when
 * {@code app.verification.expose-code} is true (development/testing), so real
 * deployments never leak the code over the wire.
 *
 * {@code email} is the address the code actually went to, so the confirmation
 * step knows which account it is confirming even when the player signed in with
 * their display name. It is filled in only where the caller has already proven
 * they own the account - by password on login, or by typing the address itself
 * on sign-up. Password recovery leaves it null on purpose: anyone can name any
 * display name, and filling this in would turn that into a way to read a
 * stranger's email address.
 */
public record CodeRequestResponse(
        String message,
        long expiresInSeconds,
        String devCode,
        String email
) {
}
