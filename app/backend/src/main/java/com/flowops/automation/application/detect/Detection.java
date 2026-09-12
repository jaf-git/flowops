package com.flowops.automation.application.detect;

import com.flowops.automation.application.shared.port.WorkspaceThresholdPort.Thresholds;
import com.flowops.shared.notice.NotificationKind;
import java.time.Instant;
import java.util.List;

public interface Detection {
    NotificationKind kind();

    List<Finding> evaluate(Instant now, Thresholds thresholds);
}
