package com.flowops.notification.application.preferences;

import com.flowops.notification.application.shared.exception.NotADisableableGroupException;
import com.flowops.notification.application.shared.exception.NotAuthenticatedException;
import com.flowops.notification.application.shared.port.IdentifyCallerPort;
import com.flowops.notification.application.shared.port.PreferencePort;
import com.flowops.shared.notice.NotificationGroup;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PreferencesService implements PreferencesUseCase {
    private final IdentifyCallerPort caller;
    private final PreferencePort preferences;

    public PreferencesService(IdentifyCallerPort caller, PreferencePort preferences) {
        this.caller = caller;
        this.preferences = preferences;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Boolean> execute() {
        UUID person = caller.currentCaller().orElseThrow(NotAuthenticatedException::new);
        Map<NotificationGroup, Boolean> stored = preferences.of(person);

        Map<String, Boolean> answer = new LinkedHashMap<>();
        for (NotificationGroup group : NotificationGroup.values()) {
            if (group.disableable()) {
                answer.put(group.name(), stored.getOrDefault(group, true));
            }
        }
        return answer;
    }

    @Override
    @Transactional
    public void update(Map<String, Boolean> wanted) {
        UUID person = caller.currentCaller().orElseThrow(NotAuthenticatedException::new);

        for (Map.Entry<String, Boolean> choice : wanted.entrySet()) {
            NotificationGroup group = disableableGroup(choice.getKey());
            preferences.set(person, group, Boolean.TRUE.equals(choice.getValue()));
        }
    }

    private NotificationGroup disableableGroup(String name) {
        NotificationGroup group;
        try {
            group = NotificationGroup.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            throw new NotADisableableGroupException(name);
        }
        if (!group.disableable()) {
            throw new NotADisableableGroupException(name);
        }
        return group;
    }
}
