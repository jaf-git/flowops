package com.flowops.automation.application.shared.port;

import com.flowops.automation.domain.EscalationIntervals;
import com.flowops.shared.time.WorkingCalendar;
import java.time.Duration;
import java.util.Optional;

public interface WorkspaceThresholdPort {
    record Thresholds(
            WorkingCalendar calendar,
            Duration stall,
            Duration block,
            Duration review,
            EscalationIntervals escalation) {}

    Optional<Thresholds> thresholds();
}
