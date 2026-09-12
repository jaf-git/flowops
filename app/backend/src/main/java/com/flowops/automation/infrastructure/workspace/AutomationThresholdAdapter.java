package com.flowops.automation.infrastructure.workspace;

import com.flowops.automation.application.shared.port.WorkspaceThresholdPort;
import com.flowops.automation.domain.EscalationIntervals;
import com.flowops.shared.time.WorkingCalendar;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AutomationThresholdAdapter implements WorkspaceThresholdPort {
    private static final String THRESHOLDS_IN_FORCE =
            """
            select working_days,
                   working_hours_start,
                   working_hours_end,
                   timezone,
                   stall_threshold_hours,
                   block_threshold_hours,
                   review_threshold_hours,
                   escalation_intervals_hours
            from workspace_settings
            where effective_to is null
            """;

    private final JdbcTemplate jdbc;

    public AutomationThresholdAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Thresholds> thresholds() {
        return jdbc
                .query(
                        THRESHOLDS_IN_FORCE,
                        (row, index) -> new Thresholds(
                                WorkingCalendar.fromMask(
                                        row.getString("working_days"),
                                        row.getTime("working_hours_start").toLocalTime(),
                                        row.getTime("working_hours_end").toLocalTime(),
                                        ZoneId.of(row.getString("timezone"))),
                                Duration.ofHours(row.getInt("stall_threshold_hours")),
                                Duration.ofHours(row.getInt("block_threshold_hours")),
                                Duration.ofHours(row.getInt("review_threshold_hours")),
                                EscalationIntervals.fromHours(row.getString("escalation_intervals_hours"))))
                .stream()
                .findFirst();
    }
}
