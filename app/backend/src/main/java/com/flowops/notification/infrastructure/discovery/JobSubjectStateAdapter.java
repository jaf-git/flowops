package com.flowops.notification.infrastructure.discovery;

import com.flowops.notification.application.shared.port.SubjectStatePort;
import com.flowops.shared.notice.CancelCondition;
import com.flowops.shared.notice.SubjectKind;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JobSubjectStateAdapter implements SubjectStatePort {
    private final JdbcTemplate jdbc;

    public JobSubjectStateAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SubjectKind answersFor() {
        return SubjectKind.JOB;
    }

    @Override
    public boolean stillRelevant(CancelCondition condition, UUID jobId) {
        return condition == CancelCondition.NEVER;
    }

    @Override
    public boolean stillExists(UUID jobId) {
        Integer count = jdbc.queryForObject("select count(*) from job where id = ?", Integer.class, jobId);
        return count != null && count > 0;
    }
}
