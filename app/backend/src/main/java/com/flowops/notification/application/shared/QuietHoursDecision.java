package com.flowops.notification.application.shared;

import com.flowops.notification.application.shared.port.WorkspaceCalendarPort;
import com.flowops.shared.notice.NotificationKind;
import com.flowops.shared.time.WorkingCalendar;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class QuietHoursDecision {
    private final WorkspaceCalendarPort calendar;

    public QuietHoursDecision(WorkspaceCalendarPort calendar) {
        this.calendar = calendar;
    }

    public Optional<Instant> holdUntil(NotificationKind kind, Instant now) {
        if (kind.timing().bypassesQuietHours()) {
            return Optional.empty();
        }
        WorkingCalendar workingCalendar = calendar.workingCalendar();
        boolean interruptible =
                workingCalendar.isWorkingTime(now) && !calendar.quietHours().covers(now);
        if (interruptible) {
            return Optional.empty();
        }
        Instant releaseAt = workingCalendar.nextWorkingInstant(now);

        return releaseAt.equals(now) ? Optional.empty() : Optional.of(releaseAt);
    }
}
