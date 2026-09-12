package com.flowops.workspace.infrastructure.time;

import com.flowops.workspace.application.shared.port.DetectServerTimezonePort;
import com.flowops.workspace.domain.model.Timezone;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

@Component
public class SystemTimezoneAdapter implements DetectServerTimezonePort {
    private static final String FALLBACK = "UTC";

    @Override
    public String detect() {
        String host = ZoneId.systemDefault().getId();
        return Timezone.known().contains(host) ? host : FALLBACK;
    }
}
