package com.flowops.notification.application.preferences;

import java.util.Map;

public interface PreferencesUseCase {
    Map<String, Boolean> execute();

    void update(Map<String, Boolean> wanted);
}
