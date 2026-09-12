package com.flowops.notification.application.shared.port;

import com.flowops.shared.notice.NotificationGroup;
import java.util.Map;
import java.util.UUID;

public interface PreferencePort {
    Map<NotificationGroup, Boolean> of(UUID person);

    void set(UUID person, NotificationGroup group, boolean enabled);
}
