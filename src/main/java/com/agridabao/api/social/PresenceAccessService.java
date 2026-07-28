package com.agridabao.api.social;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PresenceAccessService {
    private final PresenceService presenceService;

    public PresenceAccessService(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    public boolean isOnline(UUID userId) {
        return presenceService.isOnline(userId);
    }
}
