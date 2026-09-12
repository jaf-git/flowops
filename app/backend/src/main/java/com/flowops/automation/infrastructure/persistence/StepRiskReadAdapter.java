package com.flowops.automation.infrastructure.persistence;

import com.flowops.automation.application.shared.port.StepRiskReadPort;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class StepRiskReadAdapter implements StepRiskReadPort {
    private static final String REACHABLE_AND_NOBODY_HAS_TAKEN_IT =
            """
            select s.id,
                   s.reachable_at,
                   i.process_owner_user_id
            from instance_step s
            join process_instance i on i.id = s.instance_id
            join workspace_membership m
              on m.user_id = i.process_owner_user_id and m.deactivated_at is null
            where s.condition = 'REACHABLE'
              and s.task_id is null
              and s.reachable_at is not null
              and i.state = 'RUNNING'
            """;

    private final JdbcTemplate jdbc;

    public StepRiskReadAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<StalledCandidate> reachableAndUnassigned() {
        return jdbc.query(
                REACHABLE_AND_NOBODY_HAS_TAKEN_IT,
                (row, index) -> new StalledCandidate(
                        row.getObject("id", UUID.class),
                        row.getTimestamp("reachable_at").toInstant(),
                        row.getObject("process_owner_user_id", UUID.class)));
    }
}
