package com.flowops.discovery.application.shared.port;

import java.util.List;
import java.util.UUID;

public interface MyTrackPort {
    List<TrackLine> myTrack(UUID viewer);

    record TrackLine(
            UUID bracketId,
            UUID conversationId,
            String address,
            String workType,
            String state,
            String closeKind,
            List<TrackNode> nodes) {}

    record TrackNode(UUID nodeId, String kind, UUID messageId) {}
}
