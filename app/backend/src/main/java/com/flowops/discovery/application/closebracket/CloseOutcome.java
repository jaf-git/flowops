package com.flowops.discovery.application.closebracket;

import com.flowops.discovery.domain.model.BracketId;
import com.flowops.discovery.domain.model.JobId;
import com.flowops.discovery.domain.model.WorkNodeWait;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CloseOutcome(
        BracketId bracket,
        JobId job,
        List<WorkNodeWait> released,
        List<WorkNodeWait> bereaved,
        List<WorkNodeWait> reTargeted,
        List<BracketId> childrenForceClosed,
        List<UUID> noticesToCancel) {
    public CloseOutcome {
        Objects.requireNonNull(bracket, "something closed");
        Objects.requireNonNull(job, "R3.2 - a bracket belongs to exactly one engagement");
        released = List.copyOf(released);
        bereaved = List.copyOf(bereaved);
        reTargeted = List.copyOf(reTargeted);
        childrenForceClosed = List.copyOf(childrenForceClosed);
        noticesToCancel = List.copyOf(noticesToCancel);
    }

    public boolean releasedAnybody() {
        return !released.isEmpty();
    }
}
