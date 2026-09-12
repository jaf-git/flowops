package com.flowops.notification.application.inbox;

import java.util.UUID;

public interface MarkReadUseCase {
    void execute(UUID notificationId);
}
