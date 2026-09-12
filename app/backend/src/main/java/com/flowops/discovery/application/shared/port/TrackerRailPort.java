package com.flowops.discovery.application.shared.port;

import java.util.List;
import java.util.UUID;

public interface TrackerRailPort {
    List<Lane> openWork();

    record Lane(UUID jobId, String client, String project, String jobName, List<Mark> marks) {}

    record Mark(
            UUID bracketId,
            UUID jobId,
            String workType,
            String performerName,
            String state,
            UUID conversationId,
            UUID messageId) {}
}
