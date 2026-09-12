package com.flowops.discovery.application.shared.port;

import com.flowops.discovery.domain.model.JobId;
import java.util.List;
import java.util.UUID;

public interface WorkStripPort {
    List<OpenWork> othersOpenHere(JobId job, UUID conversationId, UUID notThisPerson);

    List<Closable> liveWorkIn(UUID conversationId);

    java.util.Set<UUID> liveBracketsStartedBy(UUID messageId);

    record OpenWork(
            UUID bracketId, UUID jobId, String workType, String destination, UUID performerId, String performerName) {}

    record Closable(
            UUID bracketId,
            UUID jobId,
            String workType,
            String destination,
            UUID performerId,
            String performerName,
            UUID closureRight,
            String holderName,
            int waiting,
            List<String> waitingHolderNames) {}
}
