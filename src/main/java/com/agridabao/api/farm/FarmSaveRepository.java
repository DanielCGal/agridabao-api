package com.agridabao.api.farm;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FarmSaveRepository extends JpaRepository<FarmSave, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FarmSave f where f.userId = :userId")
    Optional<FarmSave> findForUpdate(@Param("userId") UUID userId);
}
