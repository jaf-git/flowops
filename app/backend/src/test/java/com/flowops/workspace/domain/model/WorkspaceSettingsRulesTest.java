package com.flowops.workspace.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.workspace.domain.exception.AnalysisThresholdInvalidException;
import com.flowops.workspace.domain.exception.AtRiskWindowInvalidException;
import com.flowops.workspace.domain.exception.EscalationIntervalsUnorderedException;
import com.flowops.workspace.domain.exception.NoWorkingDaysException;
import com.flowops.workspace.domain.exception.QuietHoursCoverTheDayException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("WORKSPACE-CONFIGURE-01")
class WorkspaceSettingsRulesTest {
    private static final LocalTime NINE = LocalTime.of(9, 0);
    private static final LocalTime FIVE = LocalTime.of(17, 0);

    @Nested
    class TheWorkingWeek {
        @Test
        void refusesAWeekWithNoWorkingDay() {
            assertThatThrownBy(() -> new WorkingWeek(Set.of(), NINE, FIVE)).isInstanceOf(NoWorkingDaysException.class);
        }

        @Test
        void acceptsASingleWorkingDay() {
            assertThatCode(() -> new WorkingWeek(EnumSet.of(DayOfWeek.WEDNESDAY), NINE, FIVE))
                    .doesNotThrowAnyException();
        }

        @Test
        void acceptsAWorkingDayThatRunsOvernight() {
            assertThatCode(() -> new WorkingWeek(EnumSet.of(DayOfWeek.MONDAY), LocalTime.of(22, 0), LocalTime.of(6, 0)))
                    .doesNotThrowAnyException();
        }

        @Test
        void theMaskReadsAsAWeekAndSurvivesBeingStored() {
            WorkingWeek week = WorkingWeek.fromMask("YYYYYNN", NINE, FIVE);

            assertThat(week.mask()).isEqualTo("YYYYYNN");
            assertThat(week.days())
                    .containsExactlyInAnyOrder(
                            DayOfWeek.MONDAY,
                            DayOfWeek.TUESDAY,
                            DayOfWeek.WEDNESDAY,
                            DayOfWeek.THURSDAY,
                            DayOfWeek.FRIDAY);
        }

        @Test
        void aWeekendOnlyMaskIsReadAsTheWeekend() {
            assertThat(WorkingWeek.fromMask("NNNNNYY", NINE, FIVE).days())
                    .containsExactlyInAnyOrder(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        }
    }

    @Nested
    class TheQuietHours {
        @Test
        void aRangeThatWrapsMidnightCoversTheHoursEitherSideOfIt() {
            QuietHours quiet = new QuietHours(LocalTime.of(22, 0), LocalTime.of(6, 0));

            assertThat(quiet.wrapsMidnight()).isTrue();
            assertThat(quiet.covers(LocalTime.of(23, 30))).as("before midnight").isTrue();
            assertThat(quiet.covers(LocalTime.of(2, 0))).as("after midnight").isTrue();
            assertThat(quiet.covers(LocalTime.of(5, 59)))
                    .as("the last quiet minute")
                    .isTrue();
            assertThat(quiet.covers(LocalTime.of(6, 0)))
                    .as("the first working minute")
                    .isFalse();
            assertThat(quiet.covers(LocalTime.of(14, 0)))
                    .as("the middle of the afternoon")
                    .isFalse();
        }

        @Test
        void anOrdinaryRangeCoversWhatLiesBetweenItsEnds() {
            QuietHours quiet = new QuietHours(LocalTime.of(1, 0), LocalTime.of(5, 0));

            assertThat(quiet.wrapsMidnight()).isFalse();
            assertThat(quiet.covers(LocalTime.of(3, 0))).isTrue();
            assertThat(quiet.covers(LocalTime.of(23, 0))).isFalse();
        }

        @Test
        void refusesARangeThatCoversTheWholeDay() {
            assertThatThrownBy(() -> new QuietHours(LocalTime.of(0, 0), LocalTime.of(0, 0)))
                    .isInstanceOf(QuietHoursCoverTheDayException.class);
        }
    }

    @Nested
    class TheEscalationLadder {
        @Test
        void refusesIntervalsThatDoNotAscend() {
            assertThatThrownBy(() -> new EscalationLadder(List.of(24, 72, 48)))
                    .isInstanceOf(EscalationIntervalsUnorderedException.class);
        }

        @Test
        void refusesTwoIntervalsAtTheSameHour() {
            assertThatThrownBy(() -> new EscalationLadder(List.of(24, 24)))
                    .isInstanceOf(EscalationIntervalsUnorderedException.class);
        }

        @Test
        void theRefusalNamesBothPositions() {
            assertThatThrownBy(() -> new EscalationLadder(List.of(24, 72, 48)))
                    .isInstanceOfSatisfying(EscalationIntervalsUnorderedException.class, failure -> {
                        assertThat(failure.earlier()).isEqualTo(1);
                        assertThat(failure.later()).isEqualTo(2);
                    });
        }

        @Test
        void acceptsAnEmptyLadder() {
            assertThatCode(() -> new EscalationLadder(List.of())).doesNotThrowAnyException();
        }

        @Test
        void theStoredFormSurvivesARoundTrip() {
            assertThat(EscalationLadder.parse("24,72,168").stored()).isEqualTo("24,72,168");
            assertThat(EscalationLadder.parse("").hours()).isEmpty();
        }
    }

    @Nested
    class TheAtRiskWindow {
        @Test
        void refusesAWindowOfZero() {
            assertThatThrownBy(() -> settingsWithWindow(0)).isInstanceOf(AtRiskWindowInvalidException.class);
        }

        @Test
        void refusesANegativeWindow() {
            assertThatThrownBy(() -> settingsWithWindow(-1)).isInstanceOf(AtRiskWindowInvalidException.class);
        }

        @Test
        void acceptsASingleHour() {
            assertThatCode(() -> settingsWithWindow(1)).doesNotThrowAnyException();
        }
    }

    @Test
    void aSupersedingRowIsHeldToTheSameRules() {
        WorkspaceSettings current = settingsWithWindow(24);

        assertThatThrownBy(() -> current.supersededBy(
                        new Timezone("Europe/Bucharest"),
                        WorkingWeek.fromMask("YYYYYNN", NINE, FIVE),
                        0,
                        EscalationLadder.parse("24"),
                        new QuietHours(LocalTime.of(22, 0), LocalTime.of(6, 0)),
                        false,
                        AnalysisThresholds.shippingDefaults(),
                        Instant.parse("2026-09-01T09:00:00Z")))
                .isInstanceOf(AtRiskWindowInvalidException.class);
    }

    @Test
    void theShippingDefaultsAreThemselvesValidAndAreAMondayToFridayWeek() {
        WorkspaceSettings defaults = WorkspaceSettings.shippingDefaults(
                WorkspaceId.of(UUID.randomUUID()), new Timezone("Europe/Bucharest"), Instant.now());

        assertThat(defaults.workingWeek().mask()).isEqualTo("YYYYYNN");
        assertThat(defaults.atRiskWindowHours()).isPositive();
        assertThat(defaults.escalationLadder().hours()).containsExactly(24, 72, 168);
        assertThat(defaults.quietHours().wrapsMidnight())
                .as("the shipped quiet hours are the wrapping case, which is the one most likely to be got wrong")
                .isTrue();
        assertThat(defaults.invitationApprovalRequired())
                .as("off by default: the switch is a grant of authority, and shipping it on would grant it")
                .isFalse();
    }

    @Test
    void theAnalyticalThresholdsRefuseValuesThatMeanNothing() {
        assertThatThrownBy(() -> new AnalysisThresholds(0, 90)).isInstanceOf(AnalysisThresholdInvalidException.class);
        assertThatThrownBy(() -> new AnalysisThresholds(101, 90)).isInstanceOf(AnalysisThresholdInvalidException.class);
        assertThatThrownBy(() -> new AnalysisThresholds(90, 0)).isInstanceOf(AnalysisThresholdInvalidException.class);
        assertThatCode(() -> new AnalysisThresholds(100, 1)).doesNotThrowAnyException();
    }

    private static WorkspaceSettings settingsWithWindow(int hours) {
        return WorkspaceSettings.rebuild(
                WorkspaceSettingsId.generate(),
                WorkspaceId.of(UUID.randomUUID()),
                new Timezone("Europe/Bucharest"),
                WorkingWeek.fromMask("YYYYYNN", NINE, FIVE),
                hours,
                EscalationLadder.parse("24,72"),
                new QuietHours(LocalTime.of(22, 0), LocalTime.of(6, 0)),
                false,
                AnalysisThresholds.shippingDefaults(),
                Instant.parse("2026-08-10T09:00:00Z"),
                null);
    }
}
