package com.flowops.notification.application.shared;

import com.flowops.notification.application.published.NoticeRequest;
import com.flowops.notification.application.shared.port.NotificationStorePort;
import org.springframework.stereotype.Component;

@Component
public class DuplicateGate {
    private final NotificationStorePort store;

    public DuplicateGate(NotificationStorePort store) {
        this.store = store;
    }

    public boolean permits(NoticeRequest request) {
        return !store.pendingAlreadyExists(request.recipient(), request.kind(), request.subject());
    }
}
