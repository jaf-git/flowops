package com.flowops.tasklib.application.metadata;

import com.flowops.tasklib.application.exception.TemplateNotFoundException;
import com.flowops.tasklib.application.port.TaskTemplatePort;
import com.flowops.tasklib.domain.MetadataField;
import com.flowops.tasklib.domain.TaskTemplate;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemplateMetadataService implements TemplateMetadataUseCase {
    private final TaskTemplatePort templates;
    private final Clock clock;

    public TemplateMetadataService(TaskTemplatePort templates, Clock clock) {
        this.templates = templates;
        this.clock = clock;
    }

    @Override
    @Transactional
    public TaskTemplate record(UUID templateId, MetadataField field, String answer) {
        TaskTemplate template = templates
                .byId(templateId)
                .orElseThrow(() -> new TemplateNotFoundException("no template with identifier " + templateId));

        TaskTemplate described = template.describedBy(template.metadata().with(field, answer), clock.instant());
        templates.save(described);
        return described;
    }
}
