package com.agridabao.api.auth;

import com.agridabao.api.user.AppUser;

import java.time.LocalDate;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String displayName,
        LocalDate dateOfBirth
) {
    public static UserResponse from(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getDateOfBirth()
        );
    }
}
