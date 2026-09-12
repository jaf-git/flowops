package com.flowops.notification.application.inbox;

import com.flowops.shared.notice.NotificationKind;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InboxRow(
        UUID id, NotificationKind kind, UUID subjectId, Instant createdAt, Instant readAt, List<InboxRow> items) {
    public InboxRow {
        items = List.copyOf(items);
    }

    public static InboxRow single(UUID id, NotificationKind kind, UUID subjectId, Instant createdAt, Instant readAt) {
        return new InboxRow(id, kind, subjectId, createdAt, readAt, List.of());
    }

    public boolean unread() {
        return items.isEmpty() ? readAt == null : items.stream().anyMatch(InboxRow::unread);
    }
}
