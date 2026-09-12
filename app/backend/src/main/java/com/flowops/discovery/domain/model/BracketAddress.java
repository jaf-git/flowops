package com.flowops.discovery.domain.model;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record BracketAddress(
        UUID conversationId, UUID counterpartyId, String projectLabel, String workType, UUID performerId) {
    public BracketAddress {
        Objects.requireNonNull(conversationId, "an address names the chat the work was spoken in");
        workType = requireWorkType(workType);
        projectLabel = blankToNull(projectLabel);
    }

    public static BracketAddress internal(UUID conversationId, String workType) {
        return new BracketAddress(conversationId, null, null, workType, null);
    }

    public BracketAddress performedBy(UUID performer) {
        return new BracketAddress(conversationId, counterpartyId, projectLabel, workType, performer);
    }

    public BracketAddress forCounterparty(UUID counterparty) {
        return new BracketAddress(conversationId, counterparty, projectLabel, workType, performerId);
    }

    public BracketAddress onProject(String project) {
        return new BracketAddress(conversationId, counterpartyId, project, workType, performerId);
    }

    public Optional<UUID> counterparty() {
        return Optional.ofNullable(counterpartyId);
    }

    public Optional<String> project() {
        return Optional.ofNullable(projectLabel);
    }

    public Optional<UUID> performer() {
        return Optional.ofNullable(performerId);
    }

    public boolean isUnclaimed() {
        return performerId == null;
    }

    public String describe() {
        return projectLabel == null ? workType : projectLabel + " › " + workType;
    }

    private static String requireWorkType(String workType) {
        if (workType == null || workType.isBlank()) {
            throw new IllegalArgumentException(
                    "an address needs a work type; it is derived from the performer's role at bracket open");
        }
        return workType.trim();
    }

    private static String blankToNull(String projectLabel) {
        return projectLabel == null || projectLabel.isBlank() ? null : projectLabel.trim();
    }
}
