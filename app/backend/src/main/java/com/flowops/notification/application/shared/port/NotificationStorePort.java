package com.flowops.notification.application.shared.port;

import com.flowops.notification.domain.Notification;
import com.flowops.shared.notice.NotificationKind;
import com.flowops.shared.notice.SubjectRef;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationStorePort {
    boolean pendingAlreadyExists(UUID recipient, NotificationKind kind, SubjectRef subject);

    void save(Notification notification);

    void update(Notification notification);

    List<Notification> dueForRelease(Instant now);

    Optional<Notification> byId(UUID id);

    List<Notification> inboxOf(UUID recipient);

    int unreadCountOf(UUID recipient);
}
