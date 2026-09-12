package com.flowops.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowops.automation.application.escalate.EscalationLadder;
import com.flowops.automation.application.shared.port.AppendEventPort;
import com.flowops.automation.application.shared.port.EscalationStatePort;
import com.flowops.automation.application.shared.port.ReportingLinePort;
import com.flowops.automation.application.shared.port.TaskRiskReadPort;
import com.flowops.automation.application.shared.port.TaskRiskReadPort.OverdueCandidate;
import com.flowops.automation.application.shared.port.WorkspaceThresholdPort.Thresholds;
import com.flowops.automation.domain.EscalationEpisode;
import com.flowops.automation.domain.EscalationIntervals;
import com.flowops.automation.domain.Rung;
import com.flowops.notification.application.published.NoticeRequest;
import com.flowops.notification.application.published.NotifyUseCase;
import com.flowops.shared.notice.NotificationKind;
import com.flowops.shared.time.WorkingCalendar;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@Tag("AUTOMATION-ESCALATE-01")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TheLadderClimbsOneRungAtATimeTest {
    private static final ZoneId BUCHAREST = ZoneId.of("Europe/Bucharest");

    private static final Instant MONDAY_MORNING = at(2026, 8, 24, 9, 0);
    private static final Instant MONDAY_EVENING = at(2026, 8, 24, 17, 0);
    private static final Instant WEDNESDAY_EVENING = at(2026, 8, 26, 17, 0);
    private static final Instant THE_FOLLOWING_FRIDAY_EVENING = at(2026, 8, 28, 17, 0);

    private static final UUID ANDREI = UUID.randomUUID();
    private static final UUID MARIA = UUID.randomUUID();
    private static final UUID IOANA = UUID.randomUUID();
    private static final UUID THE_TASK = UUID.randomUUID();

    @Mock
    private TaskRiskReadPort tasks;

    @Mock
    private ReportingLinePort reportingLine;

    @Mock
    private NotifyUseCase notifications;

    private RememberedEpisodes episodes;
    private EscalationLadder ladder;

    private final java.util.List<Integer> rungsRecorded = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() {
        episodes = new RememberedEpisodes();
        rungsRecorded.clear();
        ladder = new EscalationLadder(tasks, reportingLine, episodes, notifications, new AppendEventPort() {
            @Override
            public void record(
                    com.flowops.automation.domain.AutomationAction action,
                    com.flowops.shared.notice.SubjectRef subject,
                    Integer rung,
                    java.time.Instant at) {
                rungsRecorded.add(rung);
            }

            @Override
            public void recordOnce(
                    com.flowops.automation.domain.AutomationAction action,
                    com.flowops.shared.notice.SubjectRef subject,
                    java.time.Instant episodeStartedAt,
                    java.time.Instant at) {
                throw new UnsupportedOperationException("the ladder records advances, never detections");
            }
        });
        when(reportingLine.reachable(ANDREI)).thenReturn(Optional.of(ANDREI));
        when(reportingLine.reachable(MARIA)).thenReturn(Optional.of(MARIA));
        when(reportingLine.reachable(IOANA)).thenReturn(Optional.of(IOANA));
        when(reportingLine.managerAbove(MARIA)).thenReturn(Optional.of(IOANA));
        when(reportingLine.managerAbove(ANDREI)).thenReturn(Optional.of(MARIA));
        when(reportingLine.managerAbove(IOANA)).thenReturn(Optional.empty());
    }

    @Test
    void twoPassesOverOneOverdueTaskProduceOneNotification() {
        overdueSinceMondayMorning(ANDREI, MARIA);

        ladder.climb(MONDAY_EVENING, ladderOfEightSixteenAndTwentyFour());
        ladder.climb(MONDAY_EVENING, ladderOfEightSixteenAndTwentyFour());

        verify(notifications, times(1)).raise(any());
        assertThat(onlyNotice().kind()).isEqualTo(NotificationKind.OVERDUE_RUNG_1);
        assertThat(onlyNotice().recipient())
                .as("rung 1 is the assignee, never their manager")
                .isEqualTo(ANDREI);
        assertThat(episodes.only().rung()).isEqualTo(Rung.ASSIGNEE);

        assertThat(rungsRecorded)
                .as("one advance, one event, carrying the rung rather than the person")
                .containsExactly(Rung.ASSIGNEE.index());
    }

    @Test
    void aRungResolvingToSomebodyAlreadyToldIsSkippedAndDoesNotConsumeAnInterval() {
        overdueSinceMondayMorning(ANDREI, ANDREI);

        ladder.climb(MONDAY_EVENING, ladderOfEightSixteenAndTwentyFour());
        ladder.climb(WEDNESDAY_EVENING, ladderOfEightSixteenAndTwentyFour());

        List<NoticeRequest> raised = everyNotice();
        assertThat(raised).hasSize(2);
        assertThat(raised.get(0).recipient()).isEqualTo(ANDREI);
        assertThat(raised.get(0).kind()).isEqualTo(NotificationKind.OVERDUE_RUNG_1);
        assertThat(raised.get(1).kind())
                .as("rung 3 fired at rung 2's interval, because the skip consumed nothing")
                .isEqualTo(NotificationKind.OVERDUE_RUNG_3);
        assertThat(raised.get(1).recipient())
                .as("the manager above the assigner, who on a self-assigned task is the assignee's own")
                .isEqualTo(MARIA);
        assertThat(raised)
                .as("Andrei is told once, not twice about the same thing")
                .filteredOn(notice -> notice.recipient().equals(ANDREI))
                .hasSize(1);
        assertThat(episodes.only().rung()).isEqualTo(Rung.MANAGER_ABOVE_ASSIGNER);
    }

    @Test
    void aPassThatMissedFourIntervalsAdvancesOneRung() {
        overdueSinceMondayMorning(ANDREI, MARIA);

        ladder.climb(THE_FOLLOWING_FRIDAY_EVENING, ladderOfEightSixteenAndTwentyFour());

        verify(notifications, times(1)).raise(any());
        assertThat(onlyNotice().kind()).isEqualTo(NotificationKind.OVERDUE_RUNG_1);
        assertThat(episodes.only().rung()).isEqualTo(Rung.ASSIGNEE);
    }

    @Test
    void aTaskThatLeavesTheOverdueStateResolvesWithoutTellingAnybody() {
        overdueSinceMondayMorning(ANDREI, MARIA);
        ladder.climb(MONDAY_EVENING, ladderOfEightSixteenAndTwentyFour());

        when(tasks.escalationCandidates(any()))
                .thenReturn(List.of(new OverdueCandidate(THE_TASK, MONDAY_MORNING, true, ANDREI, MARIA)));
        ladder.climb(WEDNESDAY_EVENING, ladderOfEightSixteenAndTwentyFour());

        assertThat(episodes.only().resolvedAt()).as("the episode closed").isEqualTo(WEDNESDAY_EVENING);
        verify(notifications, times(1)).raise(any());
        assertThat(episodes.openEpisode(THE_TASK))
                .as("a fresh overdue episode later would begin again at rung 1")
                .isEmpty();
    }

    @Test
    void anEpisodeThatFiredAfterNowIsLeftLiveRatherThanResolvedBeforeItBegan() {
        overdueSinceMondayMorning(ANDREI, MARIA);
        ladder.climb(WEDNESDAY_EVENING, ladderOfEightSixteenAndTwentyFour());
        assertThat(episodes.only().lastFiredAt()).isEqualTo(WEDNESDAY_EVENING);

        when(tasks.escalationCandidates(any()))
                .thenReturn(List.of(new OverdueCandidate(THE_TASK, MONDAY_MORNING, true, ANDREI, MARIA)));
        ladder.climb(MONDAY_EVENING, ladderOfEightSixteenAndTwentyFour());

        assertThat(episodes.only().resolvedAt())
                .as("resolving here would write an instant the constraint refuses, so nothing is written")
                .isNull();
        assertThat(episodes.openEpisode(THE_TASK))
                .as("it stays live, and resolves itself the moment the clock passes it again")
                .isPresent();
    }

    @Test
    void aTaskThatWasNeverOverdueIsNeverEscalatedAndOpensNoEpisode() {
        when(tasks.escalationCandidates(any()))
                .thenReturn(List.of(new OverdueCandidate(THE_TASK, at(2026, 9, 30, 9, 0), false, ANDREI, MARIA)));

        ladder.climb(MONDAY_EVENING, ladderOfEightSixteenAndTwentyFour());

        verify(notifications, never()).raise(any());
        assertThat(episodes.rows).isEmpty();
    }

    private void overdueSinceMondayMorning(UUID assignee, UUID assigner) {
        when(tasks.escalationCandidates(any()))
                .thenReturn(List.of(new OverdueCandidate(THE_TASK, MONDAY_MORNING, false, assignee, assigner)));
    }

    private static Thresholds ladderOfEightSixteenAndTwentyFour() {
        return new Thresholds(
                WorkingCalendar.fromMask("YYYYYNN", LocalTime.of(9, 0), LocalTime.of(17, 0), BUCHAREST),
                Duration.ofHours(48),
                Duration.ofHours(48),
                Duration.ofHours(24),
                EscalationIntervals.fromHours("8,16,24"));
    }

    private static Instant at(int year, int month, int day, int hour, int minute) {
        return LocalDate.of(year, month, day)
                .atTime(hour, minute)
                .atZone(BUCHAREST)
                .toInstant();
    }

    private NoticeRequest onlyNotice() {
        return everyNotice().get(0);
    }

    private List<NoticeRequest> everyNotice() {
        ArgumentCaptor<NoticeRequest> raised = ArgumentCaptor.forClass(NoticeRequest.class);
        verify(notifications, org.mockito.Mockito.atLeastOnce()).raise(raised.capture());
        return raised.getAllValues();
    }

    private static final class RememberedEpisodes implements EscalationStatePort {
        private record Row(UUID id, UUID taskId, Rung rung, Instant lastFiredAt, Instant resolvedAt) {}

        private final List<Row> rows = new ArrayList<>();

        @Override
        public Optional<EscalationEpisode> openEpisode(UUID taskId) {
            return live(taskId)
                    .map(row -> new EscalationEpisode(row.id(), row.taskId(), row.rung(), row.lastFiredAt()));
        }

        @Override
        public EscalationEpisode open(UUID taskId, Instant overdueSince) {
            if (live(taskId).isEmpty()) {
                rows.add(new Row(UUID.randomUUID(), taskId, Rung.NONE, overdueSince, null));
            }
            return openEpisode(taskId).orElseThrow();
        }

        @Override
        public boolean advance(EscalationEpisode seen, Rung to, Instant firedAt) {
            for (int index = 0; index < rows.size(); index++) {
                Row row = rows.get(index);
                boolean stillAsItWasRead = row.id().equals(seen.id())
                        && row.rung() == seen.rung()
                        && row.lastFiredAt().equals(seen.lastFiredAt())
                        && row.resolvedAt() == null;
                if (stillAsItWasRead) {
                    rows.set(index, new Row(row.id(), row.taskId(), to, firedAt, null));
                    return true;
                }
            }
            return false;
        }

        @Override
        public void resolve(UUID episodeId, Instant at) {
            for (int index = 0; index < rows.size(); index++) {
                Row row = rows.get(index);
                if (row.id().equals(episodeId) && row.resolvedAt() == null) {
                    rows.set(index, new Row(row.id(), row.taskId(), row.rung(), row.lastFiredAt(), at));
                }
            }
        }

        private Optional<Row> live(UUID taskId) {
            return rows.stream()
                    .filter(row -> row.taskId().equals(taskId) && row.resolvedAt() == null)
                    .findFirst();
        }

        private Row only() {
            assertThat(rows).hasSize(1);
            return rows.get(0);
        }
    }
}
