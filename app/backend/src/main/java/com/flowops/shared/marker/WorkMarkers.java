package com.flowops.shared.marker;

import com.flowops.shared.time.WorkingCalendar;
import java.time.Duration;
import java.time.Instant;

public final class WorkMarkers {
    private WorkMarkers() {}

    public static boolean overdue(Instant deadline, boolean settled, Instant now) {
        return deadline != null && !settled && deadline.isBefore(now);
    }

    public static boolean atRisk(Instant deadline, boolean settled, Instant now, Duration window) {
        if (deadline == null || settled || overdue(deadline, false, now)) {
            return false;
        }
        return !deadline.isAfter(now.plus(window));
    }

    public static boolean beyond(Instant openedAt, Instant now, Duration threshold, WorkingCalendar calendar) {
        return openedAt != null && calendar.hasElapsed(openedAt, now, threshold);
    }
}
