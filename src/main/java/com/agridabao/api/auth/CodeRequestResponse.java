package com.agridabao.api.auth;

public record CodeRequestResponse(
        String message,
        long expiresInSeconds,
        String devCode,
        String email
) {
}
