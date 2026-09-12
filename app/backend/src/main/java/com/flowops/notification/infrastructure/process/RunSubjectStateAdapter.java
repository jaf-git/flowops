package com.flowops.notification.infrastructure.process;

import com.flowops.notification.application.shared.port.SubjectStatePort;
import com.flowops.shared.notice.CancelCondition;
import com.flowops.shared.notice.SubjectKind;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class RunSubjectStateAdapter implements SubjectStatePort {
    private final JdbcTemplate jdbc;

    public RunSubjectStateAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SubjectKind answersFor() {
        return SubjectKind.RUN;
    }

    @Override
    public boolean stillRelevant(CancelCondition condition, UUID runId) {
        return condition == CancelCondition.NEVER;
    }

    @Override
    public boolean stillExists(UUID runId) {
        Integer count = jdbc.queryForObject("select count(*) from process_instance where id = ?", Integer.class, runId);
        return count != null && count > 0;
    }
}
