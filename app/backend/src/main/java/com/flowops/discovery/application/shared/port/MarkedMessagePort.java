package com.flowops.discovery.application.shared.port;

import java.util.List;
import java.util.UUID;

public interface MarkedMessagePort {
    List<Mark> marksIn(UUID conversationId);

    record Mark(
            UUID messageId,
            UUID nodeId,
            UUID bracketId,
            String workType,
            String address,
            String project,
            String client,
            String title,
            String state,
            String closeKind,
            UUID jobId,
            String jobName,
            UUID performerId,
            String performerName,
            boolean boundary,
            String activity) {}
}
