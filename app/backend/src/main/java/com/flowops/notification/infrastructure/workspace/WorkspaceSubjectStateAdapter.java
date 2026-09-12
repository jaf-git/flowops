package com.flowops.notification.infrastructure.workspace;

import com.flowops.notification.application.shared.port.SubjectStatePort;
import com.flowops.notification.application.shared.port.WeeklySectionPort;
import com.flowops.shared.notice.CancelCondition;
import com.flowops.shared.notice.SubjectKind;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceSubjectStateAdapter implements SubjectStatePort {
    private final JdbcTemplate jdbc;
    private final List<WeeklySectionPort> sections;

    public WorkspaceSubjectStateAdapter(JdbcTemplate jdbc, List<WeeklySectionPort> sections) {
        this.jdbc = jdbc;
        this.sections = sections;
    }

    @Override
    public SubjectKind answersFor() {
        return SubjectKind.WORKSPACE;
    }

    @Override
    public boolean stillRelevant(CancelCondition condition, UUID workspaceId) {
        return switch (condition) {
            case EVERY_SECTION_EMPTY -> sections.stream().anyMatch(section -> section.count() > 0);
            case NEVER -> true;
            default -> false;
        };
    }

    @Override
    public boolean stillExists(UUID workspaceId) {
        Integer count = jdbc.queryForObject("select count(*) from workspace where id = ?", Integer.class, workspaceId);
        return count != null && count > 0;
    }
}
