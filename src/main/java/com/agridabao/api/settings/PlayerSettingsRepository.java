package com.agridabao.api.settings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PlayerSettingsRepository extends JpaRepository<PlayerSettings, UUID> {
}
