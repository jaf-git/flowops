package com.flowops.discovery.infrastructure.chat;

import com.flowops.discovery.application.shared.port.MarkableMessagePort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class MarkableMessageAdapter implements MarkableMessagePort {
    private final JdbcTemplate jdbc;

    public MarkableMessageAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String READ_MARKABLE_MESSAGE =
            """
            select m.body, m.author_id, m.conversation_id
            from message m
            join conversation_participant p
              on p.conversation_id = m.conversation_id
             and p.person_id = ?
            where m.id = ?
              and m.deleted_at is null
            """;

    @Override
    public Optional<Words> read(UUID messageId, UUID callerId) {
        return jdbc
                .query(
                        READ_MARKABLE_MESSAGE,
                        (row, index) -> new Words(
                                row.getString("body"),
                                row.getObject("author_id", UUID.class),
                                row.getObject("conversation_id", UUID.class)),
                        callerId,
                        messageId)
                .stream()
                .findFirst();
    }

    private static final String ALREADY_MARKED =
            """
            select exists (select 1 from work_node_evidence where message_id = ?)
            """;

    @Override
    public boolean alreadyMarked(UUID messageId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(ALREADY_MARKED, Boolean.class, messageId));
    }

    private static final String PARTICIPATES_IN =
            """
            select exists (
                select 1 from conversation_participant
                where conversation_id = ? and person_id = ?
            )
            """;

    @Override
    public boolean participatesIn(UUID conversationId, UUID callerId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(PARTICIPATES_IN, Boolean.class, conversationId, callerId));
    }
}
