package com.agridabao.api.social;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class FriendshipAccessService {
    private final FriendshipRepository repository;

    public FriendshipAccessService(FriendshipRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public boolean areFriends(UUID first, UUID second) {
        if (first == null || second == null || first.equals(second)) return false;
        FriendshipId id = first.compareTo(second) < 0
                ? new FriendshipId(first, second)
                : new FriendshipId(second, first);
        return repository.existsById(id);
    }
}
