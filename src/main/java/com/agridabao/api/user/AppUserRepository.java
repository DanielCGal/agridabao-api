package com.agridabao.api.user;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByEmail(String email);
    boolean existsByEmail(String email);

    /**
     * Sign-in and password recovery accept a display name in place of an email.
     *
     * findFirst rather than findBy: a unique index guarantees at most one match,
     * but a duplicate slipping past it should cost one player an odd lookup, not
     * throw NonUniqueResultException and break sign-in for everyone.
     */
    Optional<AppUser> findFirstByDisplayNameIgnoreCase(String displayName);

    boolean existsByDisplayNameIgnoreCase(String displayName);

    boolean existsByDisplayNameIgnoreCaseAndIdNot(String displayName, UUID excludedUserId);

    List<AppUser> findByDisplayNameContainingIgnoreCaseAndIdNot(
            String displayName,
            UUID excludedUserId,
            Pageable pageable
    );
}
