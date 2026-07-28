package com.agridabao.api.settings;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/settings/me")
public class PlayerSettingsController {
    private final PlayerSettingsService service;

    public PlayerSettingsController(PlayerSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public PlayerSettingsResponse get(@AuthenticationPrincipal Jwt jwt) {
        return service.get(userId(jwt));
    }

    @PutMapping
    public PlayerSettingsResponse replace(@AuthenticationPrincipal Jwt jwt,
                                          @Valid @RequestBody PlayerSettingsRequest request) {
        return service.upsert(userId(jwt), request);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
