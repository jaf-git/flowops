package com.flowops.nodepipeline.domain.job;

import java.time.LocalDate;
import java.util.Set;

public record PipelineJob(
        String id,
        String name,
        String status,
        boolean standing,
        boolean shapeEligible,
        boolean rework,
        String reworkOfJobId,
        String counterpartyKind,
        LocalDate lastActivityAt) {
    private static final Set<String> FINISHED = Set.of("CLOSED", "AUTO_CLOSED", "READY_TO_CLOSE", "FORCE_CLOSED");

    public boolean isFinished(boolean hasEndNode) {
        return FINISHED.contains(status) || hasEndNode;
    }

    public String scope() {
        if (counterpartyKind == null) {
            return "UNCLASSIFIED";
        }
        return switch (counterpartyKind) {
            case "INTERNAL", "SUPPLIER", "PROSPECT" -> counterpartyKind;
            default -> "CLIENT";
        };
    }
}
