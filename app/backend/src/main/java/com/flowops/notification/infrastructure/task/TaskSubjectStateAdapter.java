package com.flowops.notification.infrastructure.task;

import com.flowops.notification.application.shared.port.SubjectStatePort;
import com.flowops.shared.notice.CancelCondition;
import com.flowops.shared.notice.SubjectKind;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TaskSubjectStateAdapter implements SubjectStatePort {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public TaskSubjectStateAdapter(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public SubjectKind answersFor() {
        return SubjectKind.TASK;
    }

    @Override
    public boolean stillRelevant(CancelCondition condition, UUID taskId) {
        return switch (condition) {
            case TASK_LEFT_CREATED -> inState(taskId, "CREATED");
            case TASK_LEFT_IN_PROGRESS -> inState(taskId, "IN_PROGRESS");
            case TASK_UNBLOCKED -> inState(taskId, "BLOCKED");

            case TASK_LEFT_REVIEW -> inState(taskId, "COMPLETED");

            case TASK_LEFT_OVERDUE -> countOf(
                            """
                            select count(*) from task
                             where id = ? and deadline < ? and state not in ('COMPLETED', 'APPROVED', 'CLOSED')
                            """,
                            taskId,
                            java.sql.Timestamp.from(clock.instant()))
                    > 0;

            case PROPOSAL_DECIDED -> countOf(
                            "select count(*) from task_deadline_proposal where task_id = ? and decision is null",
                            taskId)
                    > 0;

            case TASK_REASSIGNED -> countOf("select count(*) from task where id = ? and state <> 'CLOSED'", taskId) > 0;

            case NEVER -> true;
            default -> false;
        };
    }

    @Override
    public boolean stillExists(UUID taskId) {
        return countOf("select count(*) from task where id = ?", taskId) > 0;
    }

    private boolean inState(UUID taskId, String state) {
        return countOf("select count(*) from task where id = ? and state = ?", taskId, state) > 0;
    }

    private int countOf(String sql, Object... arguments) {
        Integer count = jdbc.queryForObject(sql, Integer.class, arguments);
        return count == null ? 0 : count;
    }
}
