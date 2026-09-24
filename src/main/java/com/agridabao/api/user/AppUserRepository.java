package com.agridabao.api.user;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByEmail(String email);
    boolean existsByEmail(String email);

    Optional<AppUser> findFirstByDisplayNameIgnoreCase(String displayName);

    boolean existsByDisplayNameIgnoreCase(String displayName);

    boolean existsByDisplayNameIgnoreCaseAndIdNot(String displayName, UUID excludedUserId);

    List<AppUser> findByDisplayNameContainingIgnoreCaseAndIdNot(
            String displayName,
            UUID excludedUserId,
            Pageable pageable
    );
}
