package com.agridabao.api.auth;

/**
 * Response for the "request a code" step. {@code devCode} is populated only when
 * {@code app.verification.expose-code} is true (development/testing), so real
 * deployments never leak the code over the wire.
 */
public record CodeRequestResponse(
        String message,
        long expiresInSeconds,
        String devCode
) {
}
