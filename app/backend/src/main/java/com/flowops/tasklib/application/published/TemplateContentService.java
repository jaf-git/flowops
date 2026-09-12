package com.flowops.tasklib.application.published;

import com.flowops.tasklib.application.port.TaskTemplatePort;
import com.flowops.tasklib.domain.TaskTemplate;
import com.flowops.tasklib.domain.TemplateDetails;
import com.flowops.tasklib.domain.TemplateStatus;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemplateContentService implements TemplateContentUseCase {
    private final TaskTemplatePort templates;

    public TemplateContentService(TaskTemplatePort templates) {
        this.templates = templates;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Content> of(UUID templateId) {
        return templates
                .byId(templateId)
                .filter(template -> template.isUsable())
                .map(template -> {
                    TemplateDetails details = template.details();
                    return new Content(details.title(), details.description(), List.copyOf(details.checklist()));
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Content> ofAll(Collection<UUID> templateIds) {
        Map<UUID, Content> found = new LinkedHashMap<>();
        for (TaskTemplate template : templates.byIds(templateIds)) {
            if (template.status() == TemplateStatus.DRAFT) {
                continue;
            }
            TemplateDetails details = template.details();
            found.put(
                    template.id(),
                    new Content(details.title(), details.description(), List.copyOf(details.checklist())));
        }
        return found;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Content> wordsForDisplay(Collection<UUID> templateIds) {
        Map<UUID, Content> found = new LinkedHashMap<>();
        for (TaskTemplate template : templates.byIds(templateIds)) {
            TemplateDetails details = template.details();
            found.put(
                    template.id(),
                    new Content(details.title(), details.description(), List.copyOf(details.checklist())));
        }
        return found;
    }
}
