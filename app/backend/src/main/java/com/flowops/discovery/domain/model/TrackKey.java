package com.flowops.discovery.domain.model;

import com.flowops.discovery.domain.enums.KeyBasis;
import java.util.Objects;
import java.util.UUID;

public record TrackKey(UUID fromRoleId, UUID toRoleId, UUID performerId, KeyBasis basis) {
    public TrackKey {
        Objects.requireNonNull(performerId, "a track is always keyed on at least a performer");
        Objects.requireNonNull(basis, "a key that does not say which rung produced it cannot be disclosed");
        if (basis == KeyBasis.ROLE_PAIR && (fromRoleId == null || toRoleId == null)) {
            throw new IllegalArgumentException(
                    "a ROLE_PAIR key needs both roles; without them the ladder has already dropped a rung");
        }
    }

    public static TrackKey byRolePair(UUID fromRoleId, UUID toRoleId, UUID performerId) {
        return new TrackKey(fromRoleId, toRoleId, performerId, KeyBasis.ROLE_PAIR);
    }

    public static TrackKey byPerformerAlone(UUID performerId) {
        return new TrackKey(null, null, performerId, KeyBasis.PERFORMER);
    }

    public static TrackKey solo(UUID roleId, UUID performerId) {
        return roleId == null ? byPerformerAlone(performerId) : byRolePair(roleId, roleId, performerId);
    }

    public boolean isSolo() {
        return fromRoleId != null && fromRoleId.equals(toRoleId);
    }

    public boolean isWeak() {
        return basis.isWeak();
    }
}
