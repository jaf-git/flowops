package com.flowops.tasklib.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.tasklib.domain.exception.InvalidRecurrenceException;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecurrenceTest {
    @Test
    @DisplayName("the 31st falls on the last day of a shorter month rather than never")
    void monthEndClamps() {
        Recurrence monthEnd = Recurrence.monthlyOn(31);

        assertThat(monthEnd.fallsOn(LocalDate.of(2026, 2, 28))).isTrue();
        assertThat(monthEnd.fallsOn(LocalDate.of(2026, 4, 30))).isTrue();
        assertThat(monthEnd.fallsOn(LocalDate.of(2026, 1, 31))).isTrue();

        assertThat(monthEnd.fallsOn(LocalDate.of(2028, 2, 29))).isTrue();
        assertThat(monthEnd.fallsOn(LocalDate.of(2028, 2, 28))).isFalse();

        assertThat(monthEnd.fallsOn(LocalDate.of(2026, 1, 30))).isFalse();
    }

    @Test
    @DisplayName("an ordinary monthly day is not clamped into the wrong month")
    void ordinaryMonthlyDay() {
        Recurrence first = Recurrence.monthlyOn(1);

        assertThat(first.fallsOn(LocalDate.of(2026, 2, 1))).isTrue();
        assertThat(first.fallsOn(LocalDate.of(2026, 2, 28))).isFalse();
    }

    @Test
    @DisplayName("weekly falls on its own day, in ISO numbering")
    void weeklyDay() {
        Recurrence thursdays = Recurrence.weeklyOn(4);

        assertThat(thursdays.fallsOn(LocalDate.of(2026, 8, 20))).isTrue();
        assertThat(thursdays.fallsOn(LocalDate.of(2026, 8, 21))).isFalse();
        assertThat(thursdays.fallsOn(LocalDate.of(2026, 8, 27))).isTrue();

        assertThat(Recurrence.weeklyOn(7).fallsOn(LocalDate.of(2026, 8, 23))).isTrue();
    }

    @Test
    @DisplayName("the next occurrence crosses a month and a year without arithmetic")
    void nextOccurrence() {
        assertThat(Recurrence.monthlyOn(1).nextOnOrAfter(LocalDate.of(2026, 12, 2)))
                .isEqualTo(LocalDate.of(2027, 1, 1));

        assertThat(Recurrence.monthlyOn(1).nextOnOrAfter(LocalDate.of(2026, 9, 1)))
                .isEqualTo(LocalDate.of(2026, 9, 1));

        assertThat(Recurrence.monthlyOn(31).nextOnOrAfter(LocalDate.of(2026, 2, 15)))
                .isEqualTo(LocalDate.of(2026, 2, 28));

        assertThat(Recurrence.daily().nextOnOrAfter(LocalDate.of(2026, 8, 20))).isEqualTo(LocalDate.of(2026, 8, 20));
    }

    @Test
    @DisplayName("a cadence with no day is refused where it is built, not interpreted later")
    void refusesAnUnevaluableRule() {
        assertThatThrownBy(() -> new Recurrence(ScheduleCadence.WEEKLY, null, null))
                .isInstanceOf(InvalidRecurrenceException.class);
        assertThatThrownBy(() -> new Recurrence(ScheduleCadence.WEEKLY, 8, null))
                .isInstanceOf(InvalidRecurrenceException.class);
        assertThatThrownBy(() -> new Recurrence(ScheduleCadence.MONTHLY, null, 0))
                .isInstanceOf(InvalidRecurrenceException.class);
    }

    @Test
    @DisplayName("a day belonging to another cadence is cleared rather than carried")
    void clearsTheDayThatDoesNotApply() {
        Recurrence daily = new Recurrence(ScheduleCadence.DAILY, 3, 15);

        assertThat(daily.dayOfWeek()).isNull();
        assertThat(daily.dayOfMonth()).isNull();
    }
}
