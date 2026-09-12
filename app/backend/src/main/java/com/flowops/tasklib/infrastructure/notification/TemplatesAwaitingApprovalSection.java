package com.flowops.tasklib.infrastructure.notification;

import com.flowops.notification.application.shared.port.WeeklySectionPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TemplatesAwaitingApprovalSection implements WeeklySectionPort {
    private final JdbcTemplate jdbc;

    public TemplatesAwaitingApprovalSection(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String key() {
        return "templatesAwaitingApproval";
    }

    @Override
    public int count() {
        Integer pending =
                jdbc.queryForObject("select count(*) from task_template where status = 'PROPOSED'", Integer.class);
        return pending == null ? 0 : pending;
    }
}
