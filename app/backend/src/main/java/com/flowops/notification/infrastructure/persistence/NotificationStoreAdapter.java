package com.flowops.notification.infrastructure.persistence;

import com.flowops.notification.application.shared.port.NotificationStorePort;
import com.flowops.notification.domain.CancelReason;
import com.flowops.notification.domain.Notification;
import com.flowops.notification.domain.NotificationState;
import com.flowops.shared.notice.NotificationKind;
import com.flowops.shared.notice.SubjectKind;
import com.flowops.shared.notice.SubjectRef;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class NotificationStoreAdapter implements NotificationStorePort {
    private static final String COLUMNS =
            """
            id, recipient_user_id, kind, subject_kind, subject_id, state, created_at,
            deliver_after, delivered_at, read_at, cancelled_at, cancel_reason
            """;

    private final JdbcTemplate jdbc;

    public NotificationStoreAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean pendingAlreadyExists(UUID recipient, NotificationKind kind, SubjectRef subject) {
        Integer found = jdbc.queryForObject(
                """
                select count(*) from notification
                 where recipient_user_id = ? and kind = ? and subject_id = ?
                   and state in ('QUEUED', 'HELD', 'DELIVERED')
                """,
                Integer.class,
                recipient,
                kind.name(),
                subject.id());
        return found != null && found > 0;
    }

    @Override
    public void save(Notification n) {
        try {
            jdbc.update(
                    "insert into notification (" + COLUMNS + ") values (?,?,?,?,?,?,?,?,?,?,?,?)",
                    n.id(),
                    n.recipient(),
                    n.kind().name(),
                    n.subject().kind().name(),
                    n.subject().id(),
                    n.state().name(),
                    Timestamp.from(n.createdAt()),
                    stamp(n.deliverAfter()),
                    stamp(n.deliveredAt()),
                    stamp(n.readAt()),
                    stamp(n.cancelledAt()),
                    n.cancelReason() == null ? null : n.cancelReason().name());
        } catch (DuplicateKeyException raced) {
            return;
        }
    }

    @Override
    public void update(Notification n) {
        jdbc.update(
                """
                update notification
                   set state = ?, deliver_after = ?, delivered_at = ?, read_at = ?,
                       cancelled_at = ?, cancel_reason = ?
                 where id = ?
                """,
                n.state().name(),
                stamp(n.deliverAfter()),
                stamp(n.deliveredAt()),
                stamp(n.readAt()),
                stamp(n.cancelledAt()),
                n.cancelReason() == null ? null : n.cancelReason().name(),
                n.id());
    }

    @Override
    public List<Notification> dueForRelease(Instant now) {
        return jdbc.query(
                "select " + COLUMNS
                        + " from notification where state = 'HELD' and deliver_after <= ? order by deliver_after",
                MAPPER,
                Timestamp.from(now));
    }

    @Override
    public Optional<Notification> byId(UUID id) {
        return jdbc.query("select " + COLUMNS + " from notification where id = ?", MAPPER, id).stream()
                .findFirst();
    }

    @Override
    public List<Notification> inboxOf(UUID recipient) {
        return jdbc.query(
                "select " + COLUMNS + " from notification where recipient_user_id = ? order by created_at desc",
                MAPPER,
                recipient);
    }

    @Override
    public int unreadCountOf(UUID recipient) {
        Integer count = jdbc.queryForObject(
                "select count(*) from notification where recipient_user_id = ? and state = 'DELIVERED'",
                Integer.class,
                recipient);
        return count == null ? 0 : count;
    }

    private static Timestamp stamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(ResultSet rows, String column) throws SQLException {
        Timestamp stamp = rows.getTimestamp(column);
        return stamp == null ? null : stamp.toInstant();
    }

    private static final RowMapper<Notification> MAPPER = (rows, index) -> new Notification(
            rows.getObject("id", UUID.class),
            rows.getObject("recipient_user_id", UUID.class),
            NotificationKind.valueOf(rows.getString("kind")),
            new SubjectRef(
                    SubjectKind.valueOf(rows.getString("subject_kind")), rows.getObject("subject_id", UUID.class)),
            NotificationState.valueOf(rows.getString("state")),
            instant(rows, "created_at"),
            instant(rows, "deliver_after"),
            instant(rows, "delivered_at"),
            instant(rows, "read_at"),
            instant(rows, "cancelled_at"),
            rows.getString("cancel_reason") == null ? null : CancelReason.valueOf(rows.getString("cancel_reason")));
}
