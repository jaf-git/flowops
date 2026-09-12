package com.flowops.aiinsight.application.port;

import java.util.UUID;

public interface ApplyProcessChangePort {
    void insertStep(UUID templateId, int position, String title);

    void removeDependency(UUID templateId, String dependentTitle, String dependsOnTitle);
}
