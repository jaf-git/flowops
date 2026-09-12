package com.flowops.discovery.application.shared.port;

import java.util.List;
import java.util.UUID;

public interface WorkSearchPort {
    List<Match> readableMatches(String query, UUID caller, int limit);

    record Match(UUID nodeId, UUID bracketId, UUID jobId, UUID conversationId, String workType, String text) {}
}
