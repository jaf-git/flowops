package com.flowops.automation.application.shared.port;

import com.flowops.automation.domain.AutomationAction;
import com.flowops.shared.notice.SubjectRef;
import java.time.Instant;

public interface AppendEventPort {
    void record(AutomationAction action, SubjectRef subject, Integer rung, Instant at);

    void recordOnce(AutomationAction action, SubjectRef subject, Instant episodeStartedAt, Instant at);
}
