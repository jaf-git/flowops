package com.flowops.discovery.application.nudge;

import com.flowops.discovery.domain.enums.NudgeAnswer;
import com.flowops.discovery.domain.enums.WorkNodeState;
import com.flowops.discovery.domain.model.JobId;
import com.flowops.discovery.domain.model.TrackId;
import com.flowops.discovery.domain.model.WorkNodeId;
import java.util.Optional;
import java.util.UUID;

public interface NudgeUseCase {
    Optional<Nudgeable> nextToAskAbout();

    Answered answer(AnswerNudge command);

    record Nudgeable(WorkNodeId node, JobId job, Optional<TrackId> track, String text, WorkNodeState state) {}

    record AnswerNudge(UUID nodeId, NudgeAnswer answer) {}

    record Answered(WorkNodeId node, WorkNodeState state, NudgeAnswer answer, boolean asksForAnOutput) {}
}
