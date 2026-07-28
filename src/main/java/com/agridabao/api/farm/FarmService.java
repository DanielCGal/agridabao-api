package com.agridabao.api.farm;

import com.agridabao.api.error.ConflictException;
import com.agridabao.api.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class FarmService {
    private final FarmSaveRepository repository;

    public FarmService(FarmSaveRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public FarmSaveResponse get(UUID userId) {
        return repository.findById(userId)
                .map(FarmSaveResponse::from)
                .orElseThrow(() -> new NotFoundException("This player does not have a saved farm yet."));
    }

    @Transactional
    public FarmSaveResponse replace(UUID userId, FarmSaveRequest request) {
        FarmSave save = repository.findForUpdate(userId).orElse(null);
        Instant now = Instant.now();

        if (save == null) {
            if (request.expectedRevision() != 0) {
                throw new ConflictException("Farm does not exist. expectedRevision must be 0 for the first save.");
            }

            save = new FarmSave(
                    userId,
                    1,
                    request.schemaVersion(),
                    request.generatorVersion(),
                    request.snapshot(),
                    now
            );
        } else {
            if (save.getRevision() != request.expectedRevision()) {
                throw new ConflictException(
                        "Farm revision conflict. Server revision is " + save.getRevision() +
                                " but the client sent " + request.expectedRevision() + "."
                );
            }

            save.replace(
                    save.getRevision() + 1,
                    request.schemaVersion(),
                    request.generatorVersion(),
                    request.snapshot(),
                    now
            );
        }

        return FarmSaveResponse.from(repository.save(save));
    }

    @Transactional
    public void recordLogout(UUID userId) {
        FarmSave save = repository.findForUpdate(userId).orElse(null);
        if (save != null) {
            save.setLastLogoutAt(Instant.now());
            repository.save(save);
        }
    }

    @Transactional
    public void delete(UUID userId) {
        repository.deleteById(userId);
    }
}
