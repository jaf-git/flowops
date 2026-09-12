package com.flowops.notification.application.shared;

import com.flowops.notification.application.shared.port.PreferencePort;
import com.flowops.shared.notice.NotificationKind;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PreferenceGate {
    private final PreferencePort preferences;

    public PreferenceGate(PreferencePort preferences) {
        this.preferences = preferences;
    }

    public boolean permits(NotificationKind kind, UUID recipient) {
        if (!kind.suppressible()) {
            return true;
        }
        return preferences.of(recipient).getOrDefault(kind.group(), true);
    }
}
