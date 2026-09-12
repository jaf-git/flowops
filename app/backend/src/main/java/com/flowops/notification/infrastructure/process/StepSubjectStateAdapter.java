package com.flowops.notification.infrastructure.process;

import com.flowops.notification.application.shared.port.SubjectStatePort;
import com.flowops.shared.notice.CancelCondition;
import com.flowops.shared.notice.SubjectKind;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class StepSubjectStateAdapter implements SubjectStatePort {
    private final JdbcTemplate jdbc;

    public StepSubjectStateAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SubjectKind answersFor() {
        return SubjectKind.STEP;
    }

    @Override
    public boolean stillRelevant(CancelCondition condition, UUID stepId) {
        return switch (condition) {
            case STEP_ASSIGNED -> countOf(
                            "select count(*) from instance_step where id = ? and condition = 'REACHABLE' and task_id is null",
                            stepId)
                    > 0;

            case TASK_DETACHED -> countOf(
                            "select count(*) from instance_step where id = ? and task_id is not null", stepId)
                    > 0;

            case NEVER -> true;
            default -> false;
        };
    }

    @Override
    public boolean stillExists(UUID stepId) {
        return countOf("select count(*) from instance_step where id = ?", stepId) > 0;
    }

    private int countOf(String sql, Object... arguments) {
        Integer count = jdbc.queryForObject(sql, Integer.class, arguments);
        return count == null ? 0 : count;
    }
}
