package com.flowops.notification.infrastructure.workspace;

import com.flowops.notification.application.shared.port.WeeklyRecipientsPort;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class WeeklyRecipientsAdapter implements WeeklyRecipientsPort {
    private final JdbcTemplate jdbc;

    public WeeklyRecipientsAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<UUID> everybodyWhoGetsTheWeekly() {
        return jdbc.query(
                """
                select u.id
                  from auth_user u
                  join workspace_membership m on m.user_id = u.id
                 where u.role_name in ('OWNER', 'MANAGER')
                   and m.deactivated_at is null
                """,
                (rows, index) -> rows.getObject("id", UUID.class));
    }
}
