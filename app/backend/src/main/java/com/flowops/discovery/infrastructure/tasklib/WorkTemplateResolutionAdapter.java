package com.flowops.discovery.infrastructure.tasklib;

import com.flowops.discovery.application.crossing.port.WorkTemplateResolutionPort;
import com.flowops.tasklib.application.published.TemplateResolutionUseCase;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class WorkTemplateResolutionAdapter implements WorkTemplateResolutionPort {
    private final TemplateResolutionUseCase templates;

    public WorkTemplateResolutionAdapter(TemplateResolutionUseCase templates) {
        this.templates = templates;
    }

    @Override
    public UUID templateFor(String title, String provenance, UUID author) {
        return templates.resolve(title, provenance, author);
    }
}
