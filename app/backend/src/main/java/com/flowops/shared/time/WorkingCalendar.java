package com.flowops.shared.time;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.Set;

public record WorkingCalendar(Set<DayOfWeek> workingDays, LocalTime dayStarts, LocalTime dayEnds, ZoneId zone) {
    private static final int MOST_DAYS_WALKED = 1826;

    private static final DayOfWeek[] MASK_ORDER = DayOfWeek.values();

    public WorkingCalendar {
        workingDays = workingDays.isEmpty() ? EnumSet.noneOf(DayOfWeek.class) : EnumSet.copyOf(workingDays);
    }

    public static WorkingCalendar fromMask(String mask, LocalTime dayStarts, LocalTime dayEnds, ZoneId zone) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (int index = 0; index < MASK_ORDER.length && index < mask.length(); index++) {
            if (mask.charAt(index) == 'Y' || mask.charAt(index) == 'y') {
                days.add(MASK_ORDER[index]);
            }
        }
        return new WorkingCalendar(days, dayStarts, dayEnds, zone);
    }

    public boolean isWorkingTime(Instant at) {
        ZonedDateTime local = at.atZone(zone);
        if (!workingDays.contains(local.getDayOfWeek())) {
            return false;
        }
        LocalTime time = local.toLocalTime();
        return !time.isBefore(dayStarts) && time.isBefore(dayEnds);
    }

    public Duration elapsedWorkingTime(Instant from, Instant to) {
        if (from == null || to == null || !to.isAfter(from)) {
            return Duration.ZERO;
        }
        Duration total = Duration.ZERO;
        LocalDate day = from.atZone(zone).toLocalDate();
        LocalDate last = to.atZone(zone).toLocalDate();
        for (int walked = 0; !day.isAfter(last) && walked < MOST_DAYS_WALKED; walked++, day = day.plusDays(1)) {
            if (!workingDays.contains(day.getDayOfWeek())) {
                continue;
            }
            Instant opens = day.atTime(dayStarts).atZone(zone).toInstant();
            Instant closes = day.atTime(dayEnds).atZone(zone).toInstant();
            Instant overlapFrom = from.isAfter(opens) ? from : opens;
            Instant overlapTo = to.isBefore(closes) ? to : closes;
            if (overlapTo.isAfter(overlapFrom)) {
                total = total.plus(Duration.between(overlapFrom, overlapTo));
            }
        }
        return total;
    }

    public boolean hasElapsed(Instant from, Instant to, Duration threshold) {
        return elapsedWorkingTime(from, to).compareTo(threshold) >= 0;
    }

    public Instant nextWorkingInstant(Instant at) {
        if (workingDays.isEmpty() || isWorkingTime(at)) {
            return at;
        }
        ZonedDateTime local = at.atZone(zone);
        LocalDate day = local.toLocalDate();

        if (!(workingDays.contains(day.getDayOfWeek()) && local.toLocalTime().isBefore(dayStarts))) {
            day = day.plusDays(1);
        }
        for (int walked = 0; walked < 8; walked++, day = day.plusDays(1)) {
            if (workingDays.contains(day.getDayOfWeek())) {
                return day.atTime(dayStarts).atZone(zone).toInstant();
            }
        }
        return at;
    }
}
