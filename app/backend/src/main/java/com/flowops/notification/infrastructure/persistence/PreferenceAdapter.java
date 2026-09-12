package com.flowops.notification.infrastructure.persistence;

import com.flowops.notification.application.shared.port.PreferencePort;
import com.flowops.shared.notice.NotificationGroup;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PreferenceAdapter implements PreferencePort {
    private final JdbcTemplate jdbc;

    public PreferenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<NotificationGroup, Boolean> of(UUID person) {
        Map<NotificationGroup, Boolean> chosen = new EnumMap<>(NotificationGroup.class);
        jdbc.query(
                "select kind_group, enabled from notification_preference where person_user_id = ?",
                rows -> {
                    chosen.put(NotificationGroup.valueOf(rows.getString("kind_group")), rows.getBoolean("enabled"));
                },
                person);
        return chosen;
    }

    @Override
    public void set(UUID person, NotificationGroup group, boolean enabled) {
        jdbc.update(
                """
                insert into notification_preference (id, person_user_id, kind_group, enabled)
                values (?, ?, ?, ?)
                on conflict (person_user_id, kind_group) do update set enabled = excluded.enabled
                """,
                UUID.randomUUID(),
                person,
                group.name(),
                enabled);
    }
}
