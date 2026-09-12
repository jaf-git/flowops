package com.flowops.notification.application.raise;

import com.flowops.notification.application.published.NoticeRequest;
import com.flowops.notification.application.published.NotifyUseCase;
import com.flowops.notification.application.shared.DuplicateGate;
import com.flowops.notification.application.shared.PreferenceGate;
import com.flowops.notification.application.shared.QuietHoursDecision;
import com.flowops.notification.application.shared.port.NotificationStorePort;
import com.flowops.notification.domain.Notification;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotifyService implements NotifyUseCase {
    private final PreferenceGate preferences;
    private final DuplicateGate duplicates;
    private final QuietHoursDecision quietHours;
    private final NotificationStorePort store;
    private final Clock clock;

    public NotifyService(
            PreferenceGate preferences,
            DuplicateGate duplicates,
            QuietHoursDecision quietHours,
            NotificationStorePort store,
            Clock clock) {
        this.preferences = preferences;
        this.duplicates = duplicates;
        this.quietHours = quietHours;
        this.store = store;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void raise(NoticeRequest request) {
        if (!preferences.permits(request.kind(), request.recipient())) {
            return;
        }
        if (!duplicates.permits(request)) {
            return;
        }

        Instant now = clock.instant();
        Optional<Instant> waitUntil = quietHours.holdUntil(request.kind(), now);
        UUID id = UUID.randomUUID();

        Notification notification = waitUntil
                .map(instant ->
                        Notification.held(id, request.recipient(), request.kind(), request.subject(), now, instant))
                .orElseGet(() -> Notification.queued(id, request.recipient(), request.kind(), request.subject(), now)
                        .delivered(now));

        store.save(notification);
    }
}
