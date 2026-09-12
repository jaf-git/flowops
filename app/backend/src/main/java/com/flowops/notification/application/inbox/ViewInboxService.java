package com.flowops.notification.application.inbox;

import com.flowops.notification.application.shared.SubjectStateRegistry;
import com.flowops.notification.application.shared.exception.NotAuthenticatedException;
import com.flowops.notification.application.shared.port.IdentifyCallerPort;
import com.flowops.notification.application.shared.port.NotificationStorePort;
import com.flowops.notification.application.shared.port.WorkspaceCalendarPort;
import com.flowops.notification.domain.Notification;
import com.flowops.notification.domain.NotificationState;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViewInboxService implements ViewInboxUseCase {
    private final IdentifyCallerPort caller;
    private final NotificationStorePort store;
    private final SubjectStateRegistry subjects;
    private final WorkspaceCalendarPort calendar;
    private final DigestComposition digests;

    public ViewInboxService(
            IdentifyCallerPort caller,
            NotificationStorePort store,
            SubjectStateRegistry subjects,
            WorkspaceCalendarPort calendar,
            DigestComposition digests) {
        this.caller = caller;
        this.store = store;
        this.subjects = subjects;
        this.calendar = calendar;
        this.digests = digests;
    }

    @Override
    @Transactional(readOnly = true)
    public List<InboxRow> execute() {
        UUID reader = caller.currentCaller().orElseThrow(NotAuthenticatedException::new);

        List<Notification> mine = store.inboxOf(reader).stream()
                .filter(notification -> notification.state() == NotificationState.DELIVERED
                        || notification.state() == NotificationState.READ)
                .map(this::withheldIfGone)
                .toList();

        return digests.compose(mine, calendar.digestThreshold());
    }

    @Override
    @Transactional(readOnly = true)
    public int unreadCount() {
        return store.unreadCountOf(caller.currentCaller().orElseThrow(NotAuthenticatedException::new));
    }

    private Notification withheldIfGone(Notification notification) {
        if (subjects.stillExists(notification.subject())) {
            return notification;
        }
        return new Notification(
                notification.id(),
                notification.recipient(),
                notification.kind(),
                null,
                notification.state(),
                notification.createdAt(),
                notification.deliverAfter(),
                notification.deliveredAt(),
                notification.readAt(),
                notification.cancelledAt(),
                notification.cancelReason());
    }
}
