package com.flowops.automation.domain;

import java.util.Optional;

public enum Rung {
    NONE,

    ASSIGNEE,

    ASSIGNER,

    MANAGER_ABOVE_ASSIGNER;

    public int index() {
        return ordinal();
    }

    public Optional<Rung> next() {
        Rung[] all = values();
        return index() + 1 < all.length ? Optional.of(all[index() + 1]) : Optional.empty();
    }

    public static Rung atIndex(int index) {
        Rung[] all = values();
        if (index < 0 || index >= all.length) {
            throw new IllegalArgumentException("no rung %d exists; the ladder has %d".formatted(index, all.length));
        }
        return all[index];
    }
}
