package com.flowops.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.flowops.automation.application.detect.Finding;
import com.flowops.automation.application.detect.LongBlockDetection;
import com.flowops.automation.application.detect.StaleReviewDetection;
import com.flowops.automation.application.detect.StalledStepDetection;
import com.flowops.automation.application.shared.port.ReportingLinePort;
import com.flowops.automation.application.shared.port.StepRiskReadPort;
import com.flowops.automation.application.shared.port.StepRiskReadPort.StalledCandidate;
import com.flowops.automation.application.shared.port.TaskRiskReadPort;
import com.flowops.automation.application.shared.port.TaskRiskReadPort.TaskInPhase;
import com.flowops.automation.application.shared.port.WorkspaceThresholdPort.Thresholds;
import com.flowops.automation.domain.EscalationIntervals;
import com.flowops.shared.notice.NotificationKind;
import com.flowops.shared.time.WorkingCalendar;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@Tag("AUTOMATION-DETECT-STALLED-STEP-01")
@Tag("AUTOMATION-DETECT-LONG-BLOCK-01")
@Tag("AUTOMATION-DETECT-STALE-REVIEW-01")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ThresholdsAreMeasuredInWorkingTimeTest {
    private static final ZoneId BUCHAREST = ZoneId.of("Europe/Bucharest");
    private static final WorkingCalendar MONDAY_TO_FRIDAY_NINE_TO_FIVE =
            WorkingCalendar.fromMask("YYYYYNN", LocalTime.of(9, 0), LocalTime.of(17, 0), BUCHAREST);

    private static final Instant FRIDAY_AFTERNOON = at(2026, 8, 21, 16, 0);

    private static final Instant SUNDAY_AFTERNOON = at(2026, 8, 23, 16, 0);
    private static final Instant TUESDAY_ONE_MINUTE_SHORT = at(2026, 8, 25, 15, 59);
    private static final Instant TUESDAY_ON_THE_THRESHOLD = at(2026, 8, 25, 16, 0);

    private static final UUID ANDREI = UUID.randomUUID();
    private static final UUID MARIA = UUID.randomUUID();
    private static final UUID SUBJECT = UUID.randomUUID();

    @Mock
    private TaskRiskReadPort tasks;

    @Mock
    private StepRiskReadPort steps;

    @Mock
    private ReportingLinePort reportingLine;

    private static Instant at(int year, int month, int day, int hour, int minute) {
        return LocalDate.of(year, month, day)
                .atTime(hour, minute)
                .atZone(BUCHAREST)
                .toInstant();
    }

    private static Thresholds twoWorkingDays() {
        Duration twoDays = Duration.ofHours(16);
        return new Thresholds(
                MONDAY_TO_FRIDAY_NINE_TO_FIVE, twoDays, twoDays, twoDays, EscalationIntervals.fromHours("24,72,168"));
    }

    @Test
    void aStepReachableOnFridayAfternoonIsNotStalledOnSunday() {
        when(steps.reachableAndUnassigned())
                .thenReturn(List.of(new StalledCandidate(SUBJECT, FRIDAY_AFTERNOON, MARIA)));
        StalledStepDetection detection = new StalledStepDetection(steps);

        assertThat(detection.evaluate(SUNDAY_AFTERNOON, twoWorkingDays()))
                .as("Sunday is 48 wall-clock hours in and no working hours at all")
                .isEmpty();
        assertThat(detection.evaluate(TUESDAY_ONE_MINUTE_SHORT, twoWorkingDays()))
                .as("one working minute short of the threshold is short of the threshold")
                .isEmpty();
    }

    @Test
    void theSameStepIsStalledOnTuesdayAndTheRunOwnerIsTold() {
        when(steps.reachableAndUnassigned())
                .thenReturn(List.of(new StalledCandidate(SUBJECT, FRIDAY_AFTERNOON, MARIA)));

        List<Finding> found = new StalledStepDetection(steps).evaluate(TUESDAY_ON_THE_THRESHOLD, twoWorkingDays());

        assertThat(found).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(NotificationKind.STEP_STALLED);
            assertThat(finding.recipient()).isEqualTo(MARIA);
            assertThat(finding.subject().id()).isEqualTo(SUBJECT);
        });
    }

    @Test
    void aTaskBlockedOnFridayAfternoonIsNotLongBlockedOnSunday() {
        when(tasks.blocked()).thenReturn(List.of(new TaskInPhase(SUBJECT, FRIDAY_AFTERNOON, ANDREI, MARIA)));
        when(reportingLine.reachable(ANDREI)).thenReturn(Optional.of(ANDREI));
        LongBlockDetection detection = new LongBlockDetection(tasks, reportingLine);

        assertThat(detection.evaluate(SUNDAY_AFTERNOON, twoWorkingDays())).isEmpty();
        assertThat(detection.evaluate(TUESDAY_ONE_MINUTE_SHORT, twoWorkingDays()))
                .isEmpty();
    }

    @Test
    void theSameTaskIsLongBlockedOnTuesdayAndTheAssigneeIsTold() {
        when(tasks.blocked()).thenReturn(List.of(new TaskInPhase(SUBJECT, FRIDAY_AFTERNOON, ANDREI, MARIA)));
        when(reportingLine.reachable(ANDREI)).thenReturn(Optional.of(ANDREI));

        List<Finding> found =
                new LongBlockDetection(tasks, reportingLine).evaluate(TUESDAY_ON_THE_THRESHOLD, twoWorkingDays());

        assertThat(found).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(NotificationKind.LONG_BLOCK_1);
            assertThat(finding.recipient())
                    .as("the assignee, whose blocker it is")
                    .isEqualTo(ANDREI);
        });
    }

    @Test
    void workCompletedOnFridayAfternoonIsNotStaleOnSunday() {
        when(tasks.awaitingReview()).thenReturn(List.of(new TaskInPhase(SUBJECT, FRIDAY_AFTERNOON, ANDREI, MARIA)));
        when(reportingLine.reachable(MARIA)).thenReturn(Optional.of(MARIA));
        StaleReviewDetection detection = new StaleReviewDetection(tasks, reportingLine);

        assertThat(detection.evaluate(SUNDAY_AFTERNOON, twoWorkingDays())).isEmpty();
        assertThat(detection.evaluate(TUESDAY_ONE_MINUTE_SHORT, twoWorkingDays()))
                .isEmpty();
    }

    @Test
    void theSameWorkIsStaleOnTuesdayAndTheReviewerIsToldRatherThanTheAssignee() {
        when(tasks.awaitingReview()).thenReturn(List.of(new TaskInPhase(SUBJECT, FRIDAY_AFTERNOON, ANDREI, MARIA)));
        when(reportingLine.reachable(MARIA)).thenReturn(Optional.of(MARIA));

        List<Finding> found =
                new StaleReviewDetection(tasks, reportingLine).evaluate(TUESDAY_ON_THE_THRESHOLD, twoWorkingDays());

        assertThat(found).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(NotificationKind.STALE_REVIEW);
            assertThat(finding.recipient())
                    .as("the person who asked for the work, never the person who finished it")
                    .isEqualTo(MARIA);
            assertThat(finding.recipient()).isNotEqualTo(ANDREI);
        });
    }
}
