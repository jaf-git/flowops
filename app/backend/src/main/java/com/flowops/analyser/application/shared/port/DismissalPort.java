package com.flowops.analyser.application.shared.port;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public interface DismissalPort {
    void dismiss(String findingKey, String analyser, String fingerprint, UUID deciderUserId, Instant decidedAt);

    Map<String, InForce> current();

    record InForce(String findingKey, String fingerprint, Instant decidedAt) {}
}
