package com.flowops.automation.application.shared.port;

import com.flowops.automation.domain.EscalationEpisode;
import com.flowops.automation.domain.Rung;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EscalationStatePort {
    Optional<EscalationEpisode> openEpisode(UUID taskId);

    EscalationEpisode open(UUID taskId, Instant overdueSince);

    boolean advance(EscalationEpisode seen, Rung to, Instant firedAt);

    void resolve(UUID episodeId, Instant at);
}
