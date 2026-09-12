package com.flowops.task.domain.model;

import com.flowops.task.domain.exception.CompletionNoteRequiredException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record CompletionProof(
        CompletionProofId id, TaskId task, String note, String externalLink, Instant submittedAt) {
    public CompletionProof {
        Objects.requireNonNull(id);
        Objects.requireNonNull(task, "a proof is evidence about a task");
        Objects.requireNonNull(submittedAt);

        String written = note == null ? "" : note.trim();
        if (written.isEmpty()) {
            throw new CompletionNoteRequiredException();
        }
        note = written;
        externalLink = externalLink == null || externalLink.isBlank() ? null : externalLink.trim();
    }

    public static CompletionProof of(TaskId task, String note, String externalLink, Instant at) {
        return new CompletionProof(CompletionProofId.generate(), task, note, externalLink, at);
    }

    public Optional<String> link() {
        return Optional.ofNullable(externalLink);
    }
}
