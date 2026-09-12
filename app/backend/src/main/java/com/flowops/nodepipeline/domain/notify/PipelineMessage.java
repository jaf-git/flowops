package com.flowops.nodepipeline.domain.notify;

import java.util.List;
import java.util.UUID;

public record PipelineMessage(
        Recipient to, MessageKind kind, String subject, String reason, double score, List<Evidence> evidence) {
    public PipelineMessage {
        if (to == null || kind == null || subject == null) {
            throw new IllegalArgumentException("a message is somebody, something to say, and what it is about");
        }
        if (to.standing() != kind.addressee()) {
            throw new IllegalArgumentException("%s is addressed to the %s and this one names a %s"
                    .formatted(kind, kind.addressee(), to.standing()));
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("never a bare score: a message says why it is being sent");
        }
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        if (evidence.isEmpty()) {
            throw new IllegalArgumentException(
                    "a recommendation carries its references, or the owner cannot check it from the graph");
        }
    }

    public record Evidence(UUID decisionId, String key, String reference) {}

    public String key() {
        return kind + "|" + to.person() + "|" + subject;
    }

    public List<String> references() {
        return evidence.stream().map(Evidence::reference).distinct().toList();
    }

    public List<UUID> decisionIds() {
        return evidence.stream().map(Evidence::decisionId).toList();
    }
}
