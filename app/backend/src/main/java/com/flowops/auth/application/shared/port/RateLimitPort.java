package com.flowops.auth.application.shared.port;

import com.flowops.auth.application.shared.AttemptPurpose;

public interface RateLimitPort {
    boolean isLimited(AttemptPurpose purpose, String subjectEmail, String ipAddress);

    void record(AttemptPurpose purpose, String subjectEmail, String ipAddress);
}
