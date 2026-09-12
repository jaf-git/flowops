package com.flowops.tasklib.application.published;

import java.util.UUID;

public interface TemplateStampUseCase {
    void recordStamp(UUID templateId);
}
