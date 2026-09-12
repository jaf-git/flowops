package com.flowops.tasklib.domain;

import com.flowops.tasklib.domain.exception.InvalidRecurrenceException;
import java.time.LocalDate;

public record Recurrence(ScheduleCadence cadence, Integer dayOfWeek, Integer dayOfMonth) {
    public Recurrence {
        if (cadence == null) {
            throw new InvalidRecurrenceException("cadence", "a schedule needs a cadence");
        }
        if (cadence == ScheduleCadence.WEEKLY && (dayOfWeek == null || dayOfWeek < 1 || dayOfWeek > 7)) {
            throw new InvalidRecurrenceException("dayOfWeek", "a weekly schedule needs a day of the week, 1 to 7");
        }
        if (cadence == ScheduleCadence.MONTHLY && (dayOfMonth == null || dayOfMonth < 1 || dayOfMonth > 31)) {
            throw new InvalidRecurrenceException("dayOfMonth", "a monthly schedule needs a day of the month, 1 to 31");
        }

        if (cadence != ScheduleCadence.WEEKLY) {
            dayOfWeek = null;
        }
        if (cadence != ScheduleCadence.MONTHLY) {
            dayOfMonth = null;
        }
    }

    public static Recurrence daily() {
        return new Recurrence(ScheduleCadence.DAILY, null, null);
    }

    public static Recurrence weeklyOn(int dayOfWeek) {
        return new Recurrence(ScheduleCadence.WEEKLY, dayOfWeek, null);
    }

    public static Recurrence monthlyOn(int dayOfMonth) {
        return new Recurrence(ScheduleCadence.MONTHLY, null, dayOfMonth);
    }

    public boolean fallsOn(LocalDate date) {
        return switch (cadence) {
            case DAILY -> true;
            case WEEKLY -> date.getDayOfWeek().getValue() == dayOfWeek;

            case MONTHLY -> date.getDayOfMonth() == Math.min(dayOfMonth, date.lengthOfMonth());
        };
    }

    public LocalDate nextOnOrAfter(LocalDate from) {
        LocalDate candidate = from;
        for (int day = 0; day <= 366; day++) {
            if (fallsOn(candidate)) {
                return candidate;
            }
            candidate = candidate.plusDays(1);
        }
        throw new IllegalStateException("a recurrence matched no date in a year: " + this);
    }
}
