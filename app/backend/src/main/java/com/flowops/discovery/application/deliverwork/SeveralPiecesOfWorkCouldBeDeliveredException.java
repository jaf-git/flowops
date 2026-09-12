package com.flowops.discovery.application.deliverwork;

import java.util.List;
import java.util.UUID;

public class SeveralPiecesOfWorkCouldBeDeliveredException extends RuntimeException {
    private final List<UUID> candidates;

    public SeveralPiecesOfWorkCouldBeDeliveredException(List<UUID> candidates) {
        super(candidates.size() + " pieces of your work are open here; say which one this delivers");
        this.candidates = List.copyOf(candidates);
    }

    public List<UUID> candidates() {
        return candidates;
    }
}
