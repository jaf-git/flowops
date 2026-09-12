package com.flowops.discovery.application.clustering;

import com.flowops.discovery.domain.enums.OutputType;
import com.flowops.discovery.domain.model.Fingerprint;
import com.flowops.discovery.domain.model.Track;
import com.flowops.discovery.domain.model.WorkNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record TrackShape(UUID fromRoleId, UUID toRoleId, OutputType terminalOutputType, List<Fingerprint> steps) {
    public TrackShape {
        steps = List.copyOf(steps);
    }

    public static Optional<TrackShape> of(Track thread, List<WorkNode> nodes) {
        List<Fingerprint> steps = new ArrayList<>();
        for (WorkNode node : nodes) {
            node.fingerprint().map(TrackShape::withoutItsClock).ifPresent(steps::add);
        }
        if (steps.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new TrackShape(
                thread.key().fromRoleId(),
                thread.key().toRoleId(),
                steps.getLast().outputType(),
                steps));
    }

    public boolean matches(TrackShape other) {
        if (other == null
                || steps.size() != other.steps.size()
                || !java.util.Objects.equals(fromRoleId, other.fromRoleId)
                || !java.util.Objects.equals(toRoleId, other.toRoleId)
                || terminalOutputType != other.terminalOutputType) {
            return false;
        }
        for (int step = 0; step < steps.size(); step++) {
            if (!steps.get(step).matches(other.steps.get(step))) {
                return false;
            }
        }
        return true;
    }

    public int length() {
        return steps.size();
    }

    private static Fingerprint withoutItsClock(Fingerprint print) {
        return Fingerprint.rehydrated(
                print.performerRoleId(),
                null,
                print.outputType(),
                print.precedingRoleId(),
                print.precedingDirection(),
                print.followingRoleId(),
                print.positionInTrack());
    }
}
