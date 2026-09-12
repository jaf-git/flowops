package com.flowops.notification.application.release;

import com.flowops.notification.application.shared.SubjectStateRegistry;
import com.flowops.notification.application.shared.port.NotificationStorePort;
import com.flowops.notification.application.shared.port.RecipientStatePort;
import com.flowops.notification.domain.CancelReason;
import com.flowops.notification.domain.Notification;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReleaseService implements ReleaseUseCase {
    private final NotificationStorePort store;
    private final SubjectStateRegistry subjects;
    private final RecipientStatePort recipients;
    private final Clock clock;

    public ReleaseService(
            NotificationStorePort store, SubjectStateRegistry subjects, RecipientStatePort recipients, Clock clock) {
        this.store = store;
        this.subjects = subjects;
        this.recipients = recipients;
        this.clock = clock;
    }

    @Override
    @Transactional
    public int release() {
        Instant now = clock.instant();
        List<Notification> due = store.dueForRelease(now);
        int delivered = 0;

        Set<UUID> stillHere =
                recipients.stillHere(due.stream().map(Notification::recipient).collect(Collectors.toSet()));

        for (Notification notification : due) {
            if (!stillHere.contains(notification.recipient())) {
                store.update(notification.cancelled(now, CancelReason.RECIPIENT_HAS_LEFT));
            } else if (subjects.stillRelevant(notification.kind().cancelCondition(), notification.subject())) {
                store.update(notification.delivered(now));
                delivered++;
            } else {
                store.update(notification.cancelled(now, CancelReason.SUBJECT_ALREADY_ACTED_ON));
            }
        }
        return delivered;
    }
}
