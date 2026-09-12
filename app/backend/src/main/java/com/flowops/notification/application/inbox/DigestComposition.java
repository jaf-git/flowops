package com.flowops.notification.application.inbox;

import com.flowops.notification.domain.Notification;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DigestComposition {
    public List<InboxRow> compose(List<Notification> delivered, int threshold) {
        Map<Instant, List<Notification>> released = new LinkedHashMap<>();
        List<InboxRow> standalone = new ArrayList<>();

        for (Notification notification : delivered) {
            boolean groupable = notification.kind().timing().digestible() && notification.deliveredAt() != null;
            if (groupable) {
                released.computeIfAbsent(notification.deliveredAt(), instant -> new ArrayList<>())
                        .add(notification);
            } else {
                standalone.add(rowFor(notification));
            }
        }

        List<InboxRow> rows = new ArrayList<>(standalone);
        for (Map.Entry<Instant, List<Notification>> group : released.entrySet()) {
            List<InboxRow> items =
                    group.getValue().stream().map(DigestComposition::rowFor).toList();
            if (items.size() > threshold) {
                rows.add(new InboxRow(null, null, null, group.getKey(), null, items));
            } else {
                rows.addAll(items);
            }
        }

        rows.sort(Comparator.comparing(InboxRow::unread)
                .reversed()
                .thenComparing(InboxRow::createdAt, Comparator.reverseOrder()));
        return rows;
    }

    private static InboxRow rowFor(Notification notification) {
        return InboxRow.single(
                notification.id(),
                notification.kind(),
                notification.subject() == null ? null : notification.subject().id(),
                notification.createdAt(),
                notification.readAt());
    }
}
