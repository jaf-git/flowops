package com.flowops.notification.infrastructure.workspace;

import com.flowops.notification.application.shared.port.RecipientStatePort;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class RecipientStateAdapter implements RecipientStatePort {
    private final JdbcTemplate jdbc;

    public RecipientStateAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<UUID> stillHere(Set<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Set.of();
        }

        return new HashSet<>(jdbc.query(
                """
                select m.user_id
                  from workspace_membership m
                 where m.deactivated_at is null
                   and m.user_id = any (?)
                """,
                (rows, index) -> rows.getObject("user_id", UUID.class),
                new Object[] {userIds.toArray(UUID[]::new)}));
    }
}
