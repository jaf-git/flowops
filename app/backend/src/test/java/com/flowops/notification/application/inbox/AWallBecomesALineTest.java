package com.flowops.notification.application.inbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.notification.domain.Notification;
import com.flowops.shared.notice.NotificationKind;
import com.flowops.shared.notice.SubjectRef;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AWallBecomesALineTest {
    private static final Instant RELEASED = Instant.parse("2026-08-24T06:00:00Z");
    private static final int THRESHOLD = 3;

    private static Notification delivered(NotificationKind kind, Instant at) {
        UUID subject = UUID.randomUUID();
        SubjectRef ref =
                switch (kind.subjectKind()) {
                    case TASK -> SubjectRef.task(subject);
                    case STEP -> SubjectRef.step(subject);
                    case RUN -> SubjectRef.run(subject);
                    case WORKSPACE -> SubjectRef.workspace(subject);
                    case BRACKET -> SubjectRef.bracket(subject);
                    case JOB -> SubjectRef.job(subject);
                };
        return Notification.held(UUID.randomUUID(), UUID.randomUUID(), kind, ref, RELEASED.minusSeconds(3600), at)
                .delivered(at);
    }

    @Test
    @DisplayName("four released together become one expandable row; each item survives inside it")
    void aboveTheThresholdTheyFold() {
        List<Notification> five = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            five.add(delivered(NotificationKind.LONG_BLOCK_1, RELEASED));
        }

        List<InboxRow> rows = new DigestComposition().compose(five, THRESHOLD);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).items())
                .as("each item is independently openable inside the digest")
                .hasSize(5);
        assertThat(rows.get(0).id())
                .as("a digest stands for its items rather than for itself")
                .isNull();
    }

    @Test
    @DisplayName("three or fewer deliver individually, because a digest of three hides three readable lines")
    void atOrBelowTheThresholdTheyDoNot() {
        List<Notification> three = List.of(
                delivered(NotificationKind.LONG_BLOCK_1, RELEASED),
                delivered(NotificationKind.STALE_REVIEW, RELEASED),
                delivered(NotificationKind.STEP_STALLED, RELEASED));

        assertThat(new DigestComposition().compose(three, THRESHOLD)).hasSize(3);
    }

    @Test
    @DisplayName("escalations among them are extracted and delivered individually, however many there are")
    void escalationsAreNeverFoldedIn() {
        List<Notification> mixed = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            mixed.add(delivered(NotificationKind.LONG_BLOCK_1, RELEASED));
        }
        mixed.add(delivered(NotificationKind.OVERDUE_RUNG_1, RELEASED));
        mixed.add(delivered(NotificationKind.OVERDUE_RUNG_2, RELEASED));

        List<InboxRow> rows = new DigestComposition().compose(mixed, THRESHOLD);

        assertThat(rows).hasSize(3);
        assertThat(rows.stream().filter(row -> !row.items().isEmpty()).count()).isEqualTo(1);
        assertThat(rows.stream().filter(row -> row.items().isEmpty()).map(InboxRow::kind))
                .containsExactlyInAnyOrder(NotificationKind.OVERDUE_RUNG_1, NotificationKind.OVERDUE_RUNG_2);
    }

    @Test
    @DisplayName("when all of them are escalations, no digest is created")
    void allEscalationsMeansNoDigest() {
        List<Notification> onlyEscalations = List.of(
                delivered(NotificationKind.OVERDUE_RUNG_1, RELEASED),
                delivered(NotificationKind.OVERDUE_RUNG_2, RELEASED),
                delivered(NotificationKind.OVERDUE_RUNG_3, RELEASED),
                delivered(NotificationKind.OVERDUE_RUNG_1, RELEASED));

        assertThat(new DigestComposition().compose(onlyEscalations, THRESHOLD))
                .hasSize(4)
                .allSatisfy(row -> assertThat(row.items()).isEmpty());
    }

    @Test
    @DisplayName("notices released at different instants are different walls and fold separately")
    void theGroupingKeyIsTheReleaseInstant() {
        List<Notification> twoMornings = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            twoMornings.add(delivered(NotificationKind.LONG_BLOCK_1, RELEASED));
        }
        for (int i = 0; i < 2; i++) {
            twoMornings.add(delivered(NotificationKind.LONG_BLOCK_1, RELEASED.plusSeconds(86_400)));
        }

        List<InboxRow> rows = new DigestComposition().compose(twoMornings, THRESHOLD);

        assertThat(rows).hasSize(3);
    }
}
