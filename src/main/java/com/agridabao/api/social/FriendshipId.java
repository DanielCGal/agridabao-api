package com.agridabao.api.social;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class FriendshipId implements Serializable {
    private UUID userLowId;
    private UUID userHighId;

    public FriendshipId() {
    }

    public FriendshipId(UUID userLowId, UUID userHighId) {
        this.userLowId = userLowId;
        this.userHighId = userHighId;
    }

    @Override
    public boolean equals(Object value) {
        if (!(value instanceof FriendshipId other)) return false;
        return Objects.equals(userLowId, other.userLowId) &&
                Objects.equals(userHighId, other.userHighId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userLowId, userHighId);
    }
}
