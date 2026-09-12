package com.flowops.discovery.infrastructure.persistence;

import com.flowops.discovery.application.shared.port.ConversationWorkPort;
import com.flowops.discovery.domain.enums.WaitKind;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ConversationWorkAdapter implements ConversationWorkPort {
    private final JdbcTemplate jdbc;

    public ConversationWorkAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ConversationBracket> workIn(UUID conversationId) {
        return jdbc.query(
                """
                select
                    b.id                as bracket_id,
                    b.work_type         as work_type,
                    b.project_label     as project_label,
                    b.state             as state,
                    b.close_kind        as close_kind,
                    b.output_value      as output_value,
                    b.output_kind       as output_kind,
                    b.performer_ref     as performer_id,
                    coalesce(u.display_name, u.email) as performer_name,
                    b.opened_at         as opened_at,
                    b.last_activity_at  as last_activity_at,
                    (b.nudged_at is not null) as nudged,
                    coalesce(array_agg(e.message_id) filter (where e.message_id is not null), '{}') as message_ids,
                    (select count(*) from work_node_wait w
                       where w.bracket_id = b.id and w.satisfied_at is null and w.cancelled_at is null)
                                        as open_waits
                from work_bracket b
                left join work_node n on n.bracket_id = b.id

                left join work_node_evidence e on e.work_node_id = n.id
                left join auth_user u on u.id = b.performer_ref
                where b.conversation_id = ?

                  and b.is_boundary = false
                group by b.id, u.display_name, u.email
                order by (b.state in ('OPEN','WAITING')) desc, b.last_activity_at desc
                """,
                (rs, row) -> {
                    java.sql.Array raw = rs.getArray("message_ids");
                    UUID[] messages = raw == null ? new UUID[0] : (UUID[]) raw.getArray();

                    String project = rs.getString("project_label");
                    String workType = rs.getString("work_type");
                    String address = project == null ? workType : project + " › " + workType;

                    return new ConversationBracket(
                            rs.getObject("bracket_id", UUID.class),
                            address,
                            workType,
                            rs.getString("state"),
                            rs.getString("close_kind"),
                            rs.getString("output_value"),
                            rs.getString("output_kind"),
                            rs.getObject("performer_id", UUID.class),
                            rs.getString("performer_name"),
                            List.of(messages),
                            rs.getTimestamp("opened_at").toInstant(),
                            rs.getTimestamp("last_activity_at").toInstant(),
                            rs.getBoolean("nudged"),
                            rs.getInt("open_waits"));
                },
                conversationId);
    }

    @Override
    public List<ConversationWait> waitsIn(UUID conversationId) {
        return jdbc.query(
                """
                select
                    w.id            as wait_id,
                    w.bracket_id    as bracket_id,
                    w.kind          as kind,
                    w.reason        as reason,
                    w.expected_by   as expected_by,
                    w.opened_at     as opened_at,
                    ob.work_type    as blocking_work_type,
                    ob.project_label as blocking_project
                from work_node_wait w
                join work_bracket b on b.id = w.bracket_id
                left join work_bracket ob on ob.id = w.on_bracket_id
                where b.conversation_id = ?
                  and w.satisfied_at is null
                  and w.cancelled_at is null
                order by coalesce(w.expected_by, w.opened_at)
                """,
                (rs, row) -> {
                    WaitKind kind = WaitKind.valueOf(rs.getString("kind"));

                    String blockingType = rs.getString("blocking_work_type");
                    String blockingProject = rs.getString("blocking_project");
                    String blocking = blockingType == null
                            ? null
                            : (blockingProject == null ? blockingType : blockingProject + " › " + blockingType);

                    Timestamp expected = rs.getTimestamp("expected_by");

                    return new ConversationWait(
                            rs.getObject("wait_id", UUID.class),
                            rs.getObject("bracket_id", UUID.class),
                            kind.name(),
                            kind.isExternal(),
                            rs.getString("reason"),
                            expected == null ? null : expected.toInstant(),
                            rs.getTimestamp("opened_at").toInstant(),
                            blocking);
                },
                conversationId);
    }
}
