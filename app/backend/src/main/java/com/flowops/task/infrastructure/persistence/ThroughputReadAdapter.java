package com.flowops.task.infrastructure.persistence;

import com.flowops.task.application.shared.TaskAudience;
import com.flowops.task.application.viewthroughput.ThroughputReadPort;
import com.flowops.task.application.viewthroughput.ViewThroughputUseCase;
import com.flowops.task.domain.model.PersonId;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ThroughputReadAdapter implements ThroughputReadPort {
    private final JdbcTemplate jdbc;

    public ThroughputReadAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ViewThroughputUseCase.Week> weeklyCounts(
            TaskAudience audience, LocalDate from, LocalDate to, ZoneId zone) {
        StringBuilder sql = new StringBuilder(
                """
                with weeks as (
                    select generate_series(date_trunc('week', ?::date),
                                           date_trunc('week', ?::date),
                                           interval '1 week')::date as starting
                ),
                moves as (
                    select date_trunc('week', t.occurred_at at time zone ?)::date as starting, t.to_state
                    from task_state_transition t
                    join task k on k.id = t.task_id
                    where t.occurred_at >= (date_trunc('week', ?::date) at time zone ?)
                      and k.kind = 'TASK'
                """);

        List<Object> arguments = new ArrayList<>();
        arguments.add(from);
        arguments.add(to);

        arguments.add(zone.getId());
        arguments.add(from);
        arguments.add(zone.getId());

        if (!audience.seesEverything()) {
            sql.append("      and (k.assignee_user_id = any (?) or k.creator_user_id = ?)\n");
            arguments.add(audience.assignees().stream().map(PersonId::value).toArray(UUID[]::new));
            arguments.add(audience.creator() == null ? null : audience.creator().value());
        }

        sql.append(
                """
                )
                select w.starting,
                       count(m.*) filter (where m.to_state = 'CREATED') as created,
                       count(m.*) filter (where m.to_state = 'CLOSED') as closed
                from weeks w
                left join moves m on m.starting = w.starting
                group by w.starting
                order by w.starting
                """);

        return jdbc.query(
                sql.toString(),
                (row, index) -> new ViewThroughputUseCase.Week(
                        row.getObject("starting", LocalDate.class), row.getInt("created"), row.getInt("closed")),
                arguments.toArray());
    }
}
