package com.flowops.aiinsight.application.port;

import java.util.UUID;

public interface ApplyTemplateChangePort {
    void retire(UUID templateId);

    void correctEstimate(UUID templateId, long medianWorkMs);
}
