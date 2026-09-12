package com.flowops.automation.infrastructure.persistence;

import com.flowops.automation.application.shared.port.TaskRiskReadPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TaskRiskReadAdapter implements TaskRiskReadPort {
    private static final String COULD_NEED_THE_LADDER =
            """
            select t.id,
                   t.deadline,
                   t.state in ('COMPLETED', 'APPROVED', 'CLOSED') as settled,
                   t.assignee_user_id,
                   t.creator_user_id
            from task t
            where t.deadline < ?
               or exists (
                   select 1 from escalation_state e
                   where e.task_id = t.id and e.resolved_at is null
               )
            """;

    private static final String IN_AN_OPEN_PHASE =
            """
            select t.id,
                   p.started_at,
                   t.assignee_user_id,
                   t.creator_user_id
            from task t
            join task_phase_timer p on p.task_id = t.id and p.ended_at is null
            where t.state = ? and p.phase_kind = ?
            """;

    private final JdbcTemplate jdbc;

    public TaskRiskReadAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<OverdueCandidate> escalationCandidates(Instant now) {
        return jdbc.query(
                COULD_NEED_THE_LADDER,
                (row, index) -> new OverdueCandidate(
                        person(row, "id"),
                        instant(row, "deadline"),
                        row.getBoolean("settled"),
                        person(row, "assignee_user_id"),
                        person(row, "creator_user_id")),
                Timestamp.from(now));
    }

    @Override
    public List<TaskInPhase> blocked() {
        return inPhase("BLOCKED", "BLOCKED");
    }

    @Override
    public List<TaskInPhase> awaitingReview() {
        return inPhase("COMPLETED", "REVIEW");
    }

    private List<TaskInPhase> inPhase(String state, String phaseKind) {
        return jdbc.query(
                IN_AN_OPEN_PHASE,
                (row, index) -> new TaskInPhase(
                        person(row, "id"),
                        instant(row, "started_at"),
                        person(row, "assignee_user_id"),
                        person(row, "creator_user_id")),
                state,
                phaseKind);
    }

    private static UUID person(ResultSet row, String column) throws SQLException {
        return row.getObject(column, UUID.class);
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        Timestamp at = row.getTimestamp(column);
        return at == null ? null : at.toInstant();
    }
}
