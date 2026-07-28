package com.agridabao.api.auth;

import java.time.Instant;

public record AuthResponse(
        String accessToken,
        Instant expiresAt,
        boolean hasFarm,
        UserResponse user
) {
}
