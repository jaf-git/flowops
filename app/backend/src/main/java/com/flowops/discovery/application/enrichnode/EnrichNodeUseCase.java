package com.flowops.discovery.application.enrichnode;

import com.flowops.discovery.domain.model.WorkNodeId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrichNodeUseCase {
    Enriched execute(Enrich command);

    record Enrich(UUID nodeId, String title, String detail, List<String> checklist) {}

    record Enriched(
            WorkNodeId node,
            Optional<String> title,
            Optional<String> detail,
            Optional<List<String>> checklist,
            boolean accepted,
            boolean correctable) {}
}
