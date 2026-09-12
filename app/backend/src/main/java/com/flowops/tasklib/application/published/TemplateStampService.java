package com.flowops.tasklib.application.published;

import com.flowops.tasklib.application.port.TaskTemplatePort;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemplateStampService implements TemplateStampUseCase {
    private final TaskTemplatePort templates;

    public TemplateStampService(TaskTemplatePort templates) {
        this.templates = templates;
    }

    @Override
    @Transactional
    public void recordStamp(UUID templateId) {
        if (templateId == null) {
            return;
        }
        templates.recordUse(templateId);
    }
}
