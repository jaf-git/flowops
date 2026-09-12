package com.flowops.discovery.infrastructure.persistence;

import com.flowops.discovery.application.shared.port.ConversationShelfPort;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ConversationShelfAdapter implements ConversationShelfPort {
    private final JdbcTemplate jdbc;

    public ConversationShelfAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ShelfItem> shelfOf(UUID conversationId) {
        return jdbc.query(
                """
                select s.id, s.kind, s.value, s.label, s.placed_by, s.placed_at,
                       coalesce(u.display_name, u.email) as placed_by_name
                from conversation_shelf s
                left join auth_user u on u.id = s.placed_by
                where s.conversation_id = ? and s.removed_at is null
                order by s.placed_at desc
                """,
                (rs, row) -> new ShelfItem(
                        rs.getObject("id", UUID.class),
                        rs.getString("kind"),
                        rs.getString("value"),
                        rs.getString("label"),
                        rs.getObject("placed_by", UUID.class),
                        rs.getString("placed_by_name"),
                        rs.getTimestamp("placed_at").toInstant()),
                conversationId);
    }

    @Override
    public void place(UUID conversationId, String kind, String value, String label, UUID placedBy, Instant at) {
        jdbc.update(
                """
                insert into conversation_shelf (id, conversation_id, kind, value, label, placed_by, placed_at)
                values (?,?,?,?,?,?,?)
                """,
                UUID.randomUUID(),
                conversationId,
                kind,
                value,
                label == null || label.isBlank() ? null : label.trim(),
                placedBy,
                Timestamp.from(at));
    }

    @Override
    public void remove(UUID itemId, Instant at) {
        jdbc.update(
                "update conversation_shelf set removed_at = ? where id = ? and removed_at is null",
                Timestamp.from(at),
                itemId);
    }
}
