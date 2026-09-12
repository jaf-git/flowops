package com.flowops.process.infrastructure.persistence;

import com.flowops.process.application.shared.port.TemplateUsesPort;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TemplateUsesAdapter implements TemplateUsesPort {
    private final JdbcTemplate jdbc;

    public TemplateUsesAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PlannedIn> plannedIn(UUID taskTemplateId) {
        return jdbc.query(
                """
                select distinct on (p.id) p.id, p.name, s.position, p.active
                from step_definition s
                join process_template p on p.id = s.template_id
                where s.task_template_id = ?
                order by p.id, s.position
                """,
                (row, index) -> new PlannedIn(
                        row.getObject("id", UUID.class),
                        row.getString("name"),
                        row.getInt("position") + 1,
                        row.getBoolean("active")),
                taskTemplateId);
    }

    @Override
    public List<CutIn> cutIn(UUID taskTemplateId, int limit) {
        return jdbc.query(
                """
                select i.id as instance_id, i.name as instance_name, i.state as instance_state,
                       s.id as step_id, s.condition as step_condition, s.task_id, i.started_at
                from instance_step s
                join process_instance i on i.id = s.instance_id
                where s.task_template_id = ?

                  and s.skipped_at is null
                order by i.started_at desc, s.position
                limit ?
                """,
                (row, index) -> new CutIn(
                        row.getObject("instance_id", UUID.class),
                        row.getString("instance_name"),
                        row.getString("instance_state"),
                        row.getObject("step_id", UUID.class),
                        row.getString("step_condition"),
                        row.getObject("task_id", UUID.class),
                        row.getTimestamp("started_at").toInstant()),
                taskTemplateId,
                limit);
    }

    @Override
    public int countCutIn(UUID taskTemplateId) {
        Integer total = jdbc.queryForObject(
                "select count(*) from instance_step where task_template_id = ? and skipped_at is null",
                Integer.class,
                taskTemplateId);
        return total == null ? 0 : total;
    }
}
