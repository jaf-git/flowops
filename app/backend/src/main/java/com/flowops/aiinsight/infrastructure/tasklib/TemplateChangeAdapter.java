package com.flowops.aiinsight.infrastructure.tasklib;

import com.flowops.aiinsight.application.port.ApplyTemplateChangePort;
import com.flowops.tasklib.application.published.TemplateLifecycleUseCase;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TemplateChangeAdapter implements ApplyTemplateChangePort {
    private final TemplateLifecycleUseCase templates;

    public TemplateChangeAdapter(TemplateLifecycleUseCase templates) {
        this.templates = templates;
    }

    @Override
    public void retire(UUID templateId) {
        templates.retire(templateId);
    }

    @Override
    public void correctEstimate(UUID templateId, long medianWorkMs) {
        templates.correctEstimate(templateId, medianWorkMs);
    }
}
