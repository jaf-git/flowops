package com.flowops.notification.infrastructure.discovery;

import com.flowops.notification.application.shared.port.SubjectStatePort;
import com.flowops.shared.notice.CancelCondition;
import com.flowops.shared.notice.SubjectKind;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class BracketSubjectStateAdapter implements SubjectStatePort {
    private final JdbcTemplate jdbc;

    public BracketSubjectStateAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SubjectKind answersFor() {
        return SubjectKind.BRACKET;
    }

    @Override
    public boolean stillRelevant(CancelCondition condition, UUID bracketId) {
        if (condition != CancelCondition.BRACKET_CLOSED) {
            return false;
        }
        Integer live = jdbc.queryForObject(
                "select count(*) from work_bracket where id = ? and state in ('OPEN', 'WAITING')",
                Integer.class,
                bracketId);
        return live != null && live > 0;
    }

    @Override
    public boolean stillExists(UUID bracketId) {
        Integer count = jdbc.queryForObject("select count(*) from work_bracket where id = ?", Integer.class, bracketId);
        return count != null && count > 0;
    }
}
