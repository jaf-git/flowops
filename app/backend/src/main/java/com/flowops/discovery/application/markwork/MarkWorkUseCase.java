package com.flowops.discovery.application.markwork;

import com.flowops.discovery.domain.enums.MarkVerb;
import com.flowops.discovery.domain.model.BracketId;
import com.flowops.discovery.domain.model.WorkNodeId;
import java.util.UUID;

public interface MarkWorkUseCase {
    Placed execute(MarkWork command);

    record MarkWork(
            UUID messageId,
            UUID jobId,
            UUID performerId,
            String workType,
            UUID activityId,
            MarkVerb verb,
            UUID joining) {}

    record Placed(
            WorkNodeId node,
            BracketId bracket,
            boolean joined,
            String destination,
            String workType,
            String activity,
            UUID joinedWith) {}
}
