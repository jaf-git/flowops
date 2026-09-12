package com.flowops.discovery.application.shared.port;

import com.flowops.discovery.domain.model.JobId;
import java.util.List;
import java.util.UUID;

public interface PublicGraphPort {
    List<PublicNode> shapeOf(JobId job);

    List<PublicEdge> edgesOf(JobId job);

    int matchesBeyondReach(String query, UUID caller);

    List<UUID> conversationsVisibleTo(UUID person);

    record PublicNode(
            UUID nodeId,
            UUID bracketId,
            String workType,
            boolean workTypeOverridden,
            String department,
            String performerName,
            boolean unclaimed,
            String markerName,
            String client,
            String projectLabel,
            String state,
            long elapsed,
            String phase,
            String closeKind,
            String nodeRole,
            boolean boundary,
            String text,
            String title,
            String detail,
            List<String> checklist,
            String direction,
            String kind,
            String outputType,
            UUID taskTemplateId,
            String activity,
            UUID messageId,
            UUID conversationId) {}

    record PublicEdge(EdgeKind kind, UUID fromNodeId, UUID toNodeId, EdgeState state) {
        public enum EdgeState {
            LIVE,

            SATISFIED,

            WITHDRAWN
        }

        public enum EdgeKind {
            PARENTAGE,

            WAIT,

            SUCCESSION
        }
    }
}
