package com.flowops.discovery.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnEngagementCanEndAndAForcedEndingSaysWhyTest {
    private static final Instant NOW = Instant.parse("2026-08-26T09:00:00Z");

    private Job anOpenJob() {
        return Job.opened(JobId.of(UUID.randomUUID()), "Sunrise Bakery · summer menu", UUID.randomUUID(), NOW);
    }

    @Test
    @DisplayName("R16.4 — a force close without a reason is refused")
    void aForcedEndingSaysWhy() {
        Job job = anOpenJob();

        assertThatThrownBy(() -> job.forceClosed(NOW, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("R16.4");

        assertThat(job.status())
                .describedAs("the refusal must leave the engagement exactly as it was, not half-closed")
                .isEqualTo(Job.Status.OPEN);
    }

    @Test
    @DisplayName("D12 — a forced ending is never evidence, and the flag is not the caller's to set")
    void aForcedEndingTeachesNothing() {
        Job job = anOpenJob();
        assertThat(job.shapeEligible()).isTrue();

        job.forceClosed(NOW, "client pulled the budget");

        assertThat(job.status()).isEqualTo(Job.Status.FORCE_CLOSED);
        assertThat(job.closeReason()).contains("client pulled the budget");
        assertThat(job.shapeEligible())
                .describedAs("D12 - the graph is truncated, so the shape it would teach never happened")
                .isFalse();
    }

    @Test
    @DisplayName("R15.8 — new work on a ready job reopens it rather than inventing a rework job")
    void aReadyJobReopens() {
        Job job = anOpenJob();

        job.readyToClose();
        assertThat(job.status()).isEqualTo(Job.Status.READY_TO_CLOSE);
        assertThat(job.isEnded())
                .describedAs("ready to close is not an ending; nothing has closed yet")
                .isFalse();

        job.reopened(NOW.plusSeconds(3600));

        assertThat(job.status()).isEqualTo(Job.Status.OPEN);
    }

    @Test
    @DisplayName("R15.8 — a job cannot auto-close while its work is still live")
    void autoCloseNeedsAReadyJob() {
        Job job = anOpenJob();

        assertThatThrownBy(() -> job.autoClosed(NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("READY_TO_CLOSE");
    }

    @Test
    @DisplayName("R16.1 — all three endings are endings, not just the one the original check knew about")
    void everyEndingIsAnEnding() {
        Job forced = anOpenJob();
        forced.forceClosed(NOW, "scope changed into a different engagement");

        Job auto = anOpenJob();
        auto.readyToClose();
        auto.autoClosed(NOW);

        Job closed = anOpenJob();
        closed.closed(NOW);

        for (Job ended : new Job[] {forced, auto, closed}) {
            assertThat(ended.isEnded()).isTrue();
            assertThatThrownBy(() -> ended.touched(NOW.plusSeconds(60)))
                    .describedAs("work that returns opens a new job linked to this one")
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("R15.5 — eligibility lost to a hole is never regained")
    void aHoleIsPermanent() {
        Job job = anOpenJob();

        job.holdsAHole();
        job.readyToClose();
        job.closed(NOW);

        assertThat(job.shapeEligible()).isFalse();
    }
}
