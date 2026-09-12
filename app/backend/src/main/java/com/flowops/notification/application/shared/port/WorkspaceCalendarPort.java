package com.flowops.notification.application.shared.port;

import com.flowops.notification.domain.QuietHours;
import com.flowops.shared.time.WorkingCalendar;

public interface WorkspaceCalendarPort {
    QuietHours quietHours();

    WorkingCalendar workingCalendar();

    int digestThreshold();
}
