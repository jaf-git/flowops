package com.flowops.notification.application.inbox;

import com.flowops.notification.application.shared.exception.NotAuthenticatedException;
import com.flowops.notification.application.shared.exception.NotYoursException;
import com.flowops.notification.application.shared.port.IdentifyCallerPort;
import com.flowops.notification.application.shared.port.NotificationStorePort;
import com.flowops.notification.domain.Notification;
import com.flowops.notification.domain.NotificationState;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarkReadService implements MarkReadUseCase {
    private final IdentifyCallerPort caller;
    private final NotificationStorePort store;
    private final Clock clock;

    public MarkReadService(IdentifyCallerPort caller, NotificationStorePort store, Clock clock) {
        this.caller = caller;
        this.store = store;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void execute(UUID notificationId) {
        UUID reader = caller.currentCaller().orElseThrow(NotAuthenticatedException::new);
        Notification notification = store.byId(notificationId).orElseThrow(NotYoursException::new);

        if (!notification.recipient().equals(reader)) {
            throw new NotYoursException();
        }
        if (notification.state() != NotificationState.DELIVERED) {
            return;
        }
        store.update(notification.read(clock.instant()));
    }
}
