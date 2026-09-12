package com.flowops.discovery.infrastructure.tasklib;

import com.flowops.discovery.application.crossing.port.TemplateCreationPort;
import com.flowops.tasklib.application.published.TemplateLifecycleUseCase;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class WorkTemplateCreationAdapter implements TemplateCreationPort {
    private final TemplateLifecycleUseCase templates;

    public WorkTemplateCreationAdapter(TemplateLifecycleUseCase templates) {
        this.templates = templates;
    }

    @Override
    public UUID createTemplateFor(String title, String detail, List<String> steps, UUID author) {
        return templates.createApproved(title, detail, steps == null ? List.of() : steps, author);
    }
}
