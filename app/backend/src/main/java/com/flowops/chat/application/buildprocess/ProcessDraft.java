package com.flowops.chat.application.buildprocess;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProcessDraft(Sourced<String> name, Sourced<UUID> processOwnerId, List<Step> steps) {
    public record Step(
            UUID messageId,
            Sourced<String> title,
            String description,
            Sourced<UUID> assigneeId,
            Sourced<Instant> deadline) {}
}
