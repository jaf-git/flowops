package com.flowops.process.infrastructure.tasklib;

import com.flowops.process.application.shared.port.RecordTemplateStampPort;
import com.flowops.process.domain.model.TaskTemplateRef;
import com.flowops.tasklib.application.published.TemplateStampUseCase;
import org.springframework.stereotype.Component;

@Component
public class RecordTemplateStampAdapter implements RecordTemplateStampPort {
    private final TemplateStampUseCase templates;

    public RecordTemplateStampAdapter(TemplateStampUseCase templates) {
        this.templates = templates;
    }

    @Override
    public void stamped(TaskTemplateRef work) {
        if (work == null) {
            return;
        }
        templates.recordStamp(work.value());
    }
}
