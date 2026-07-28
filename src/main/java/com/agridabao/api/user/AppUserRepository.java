package com.agridabao.api.user;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByEmail(String email);
    boolean existsByEmail(String email);

    List<AppUser> findByDisplayNameContainingIgnoreCaseAndIdNot(
            String displayName,
            UUID excludedUserId,
            Pageable pageable
    );
}
