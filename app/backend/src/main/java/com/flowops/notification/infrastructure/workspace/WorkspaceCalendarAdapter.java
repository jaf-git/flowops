package com.flowops.notification.infrastructure.workspace;

import com.flowops.notification.application.shared.port.WorkspaceCalendarPort;
import com.flowops.notification.domain.QuietHours;
import com.flowops.shared.time.WorkingCalendar;
import java.time.ZoneId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceCalendarAdapter implements WorkspaceCalendarPort {
    private final JdbcTemplate jdbc;

    public WorkspaceCalendarAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public QuietHours quietHours() {
        return jdbc.queryForObject(
                """
                select quiet_hours_start, quiet_hours_end, timezone
                  from workspace_settings where effective_to is null
                """,
                (rows, index) -> new QuietHours(
                        rows.getTime("quiet_hours_start").toLocalTime(),
                        rows.getTime("quiet_hours_end").toLocalTime(),
                        ZoneId.of(rows.getString("timezone"))));
    }

    @Override
    public WorkingCalendar workingCalendar() {
        return jdbc.queryForObject(
                """
                select working_days, working_hours_start, working_hours_end, timezone
                  from workspace_settings where effective_to is null
                """,
                (rows, index) -> WorkingCalendar.fromMask(
                        rows.getString("working_days"),
                        rows.getTime("working_hours_start").toLocalTime(),
                        rows.getTime("working_hours_end").toLocalTime(),
                        ZoneId.of(rows.getString("timezone"))));
    }

    @Override
    public int digestThreshold() {
        Integer threshold = jdbc.queryForObject(
                "select digest_threshold from workspace_settings where effective_to is null", Integer.class);
        if (threshold == null) {
            throw new IllegalStateException("no workspace settings are in force, so there is no digest threshold");
        }
        return threshold;
    }
}
