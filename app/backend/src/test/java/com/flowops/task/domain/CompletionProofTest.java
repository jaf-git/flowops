package com.flowops.task.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.task.domain.exception.CompletionNoteRequiredException;
import com.flowops.task.domain.model.CompletionProof;
import com.flowops.task.domain.model.TaskId;
import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("TASK-COMPLETE-01")
class CompletionProofTest {
    private static final Instant NOW = Instant.parse("2026-08-11T14:00:00Z");

    @Test
    void aProofWithNoNoteIsRefused() {
        assertThatThrownBy(() -> CompletionProof.of(TaskId.generate(), null, null, NOW))
                .isInstanceOf(CompletionNoteRequiredException.class);
        assertThatThrownBy(() -> CompletionProof.of(TaskId.generate(), "   ", null, NOW))
                .as("a note of spaces reaches the reviewer as an empty panel they cannot act on")
                .isInstanceOf(CompletionNoteRequiredException.class);
    }

    @Test
    void theNoteIsTrimmedBeforeItIsStored() {
        CompletionProof proof =
                CompletionProof.of(TaskId.generate(), "  Compared both quarters and sent the summary.  ", null, NOW);

        assertThat(proof.note()).isEqualTo("Compared both quarters and sent the summary.");
    }

    @Test
    void aLinkIsCarriedAsTextAndAnAbsentOneIsAbsentRatherThanEmpty() {
        TaskId task = TaskId.generate();

        assertThat(CompletionProof.of(task, "Done.", "https://drive.example.ro/q3-review", NOW)
                        .link())
                .contains("https://drive.example.ro/q3-review");
        assertThat(CompletionProof.of(task, "Done.", "  ", NOW).link())
                .as("a blank link is no link, not an empty one a reviewer can click")
                .isEmpty();
    }
}
