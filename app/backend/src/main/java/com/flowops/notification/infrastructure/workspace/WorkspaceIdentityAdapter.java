package com.flowops.notification.infrastructure.workspace;

import com.flowops.notification.application.weekly.WorkspaceIdentityPort;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceIdentityAdapter implements WorkspaceIdentityPort {
    private final JdbcTemplate jdbc;

    public WorkspaceIdentityAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UUID current() {
        return jdbc.queryForObject("select id from workspace order by id limit 1", UUID.class);
    }
}
