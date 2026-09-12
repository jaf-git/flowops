package com.flowops.automation.infrastructure.workspace;

import com.flowops.automation.application.shared.port.ReportingLinePort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ReportingLineAdapter implements ReportingLinePort {
    private static final String STILL_A_MEMBER =
            """
            select m.user_id
            from workspace_membership m
            where m.user_id = ? and m.deactivated_at is null
            """;

    private static final String ONE_LEVEL_UP =
            """
            select above.user_id
            from workspace_membership m
            join workspace_membership above on above.id = m.manager_id
            where m.user_id = ?
            """;

    private final JdbcTemplate jdbc;

    public ReportingLineAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UUID> reachable(UUID person) {
        return person == null ? Optional.empty() : one(STILL_A_MEMBER, person);
    }

    @Override
    public Optional<UUID> managerAbove(UUID person) {
        return person == null ? Optional.empty() : one(ONE_LEVEL_UP, person);
    }

    private Optional<UUID> one(String query, UUID person) {
        return jdbc.query(query, (row, index) -> row.getObject("user_id", UUID.class), person).stream()
                .findFirst();
    }
}
