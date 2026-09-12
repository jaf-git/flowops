package com.flowops.workspace.domain.model;

import com.flowops.workspace.domain.exception.NoWorkingDaysException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

public record WorkingWeek(Set<DayOfWeek> days, LocalTime start, LocalTime end) {
    public WorkingWeek {
        java.util.Objects.requireNonNull(start, "a working day has a start");
        java.util.Objects.requireNonNull(end, "a working day has an end");
        days = days == null ? Set.of() : EnumSet.copyOf(days.isEmpty() ? EnumSet.noneOf(DayOfWeek.class) : days);
        if (days.isEmpty()) {
            throw new NoWorkingDaysException();
        }
    }

    public static WorkingWeek fromMask(String mask, LocalTime start, LocalTime end) {
        EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        String value = mask == null ? "" : mask;
        for (int index = 0; index < Math.min(value.length(), 7); index++) {
            if (value.charAt(index) == 'Y') {
                days.add(DayOfWeek.of(index + 1));
            }
        }
        return new WorkingWeek(days, start, end);
    }

    public String mask() {
        StringBuilder mask = new StringBuilder();
        for (DayOfWeek day : DayOfWeek.values()) {
            mask.append(days.contains(day) ? 'Y' : 'N');
        }
        return mask.toString();
    }
}
