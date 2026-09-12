package com.flowops.tasklib.application.published;

import com.flowops.tasklib.application.TaskTemplateUseCase;
import com.flowops.tasklib.application.exception.NotTheAuthorException;
import com.flowops.tasklib.application.port.TaskTemplatePort;
import com.flowops.tasklib.domain.MetadataField;
import com.flowops.tasklib.domain.TaskTemplate;
import com.flowops.tasklib.domain.TemplateDetails;
import com.flowops.tasklib.domain.TemplateMetadata;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemplateLifecycleService implements TemplateLifecycleUseCase {
    private final TaskTemplateUseCase templates;
    private final TemplateResolutionUseCase resolution;
    private final TaskTemplatePort library;

    public TemplateLifecycleService(
            TaskTemplateUseCase templates, TemplateResolutionUseCase resolution, TaskTemplatePort library) {
        this.templates = templates;
        this.resolution = resolution;
        this.library = library;
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('TASK_TEMPLATE_RETIRE')")
    public void retire(UUID templateId) {
        TemplateRefusals.refusingAsPublished(() -> templates.retire(templateId));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('TASK_TEMPLATE_CREATE')")
    public void correctEstimate(UUID templateId, long medianWorkMs) {
        TemplateRefusals.refusingAsPublished(() -> {
            TaskTemplate template = templates.byId(templateId);
            BigDecimal hours =
                    BigDecimal.valueOf(medianWorkMs).divide(BigDecimal.valueOf(3_600_000L), 2, RoundingMode.HALF_UP);
            TemplateDetails corrected = new TemplateDetails(
                    template.details().title(),
                    template.details().description(),
                    template.details().type(),
                    template.details().priority(),
                    hours,
                    template.details().checklist());

            templates.edit(templateId, corrected, false);
        });
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('TASK_TEMPLATE_CREATE') and hasAuthority('TASK_TEMPLATE_APPROVE')")
    public UUID createApproved(String title, String description, List<String> checklist, UUID author) {
        UUID[] resolved = new UUID[1];
        TemplateRefusals.refusingAsPublished(() -> {
            resolved[0] = resolution.resolve(title, description, author);
            if (checklist == null || checklist.isEmpty()) {
                return;
            }

            TaskTemplate template = templates.byId(resolved[0]);
            if (!template.details().checklist().isEmpty()) {
                return;
            }

            TemplateDetails withSteps = new TemplateDetails(
                    template.details().title(),
                    template.details().description(),
                    template.details().type(),
                    template.details().priority(),
                    template.details().estimatedHours(),
                    checklist);
            try {
                templates.edit(resolved[0], withSteps, false);
            } catch (NotTheAuthorException somebodyElseWroteIt) {
            }
        });
        return resolved[0];
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('TASK_TEMPLATE_CREATE')")
    public void describeDiscovered(UUID templateId, TaskTemplatePort.DiscoveredFacts facts) {
        library.recordDiscovered(templateId, facts);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('TASK_TEMPLATE_CREATE')")
    public DraftedTemplate createDraft(
            String title,
            String description,
            List<String> checklist,
            String responsibleRole,
            String outputKind,
            UUID author) {
        DraftedTemplate[] drafted = new DraftedTemplate[1];
        TemplateRefusals.refusingAsPublished(() -> drafted[0] = resolution
                .findMeaning(title)
                .map(found -> new DraftedTemplate(found, false))
                .or(() -> library.earliestDraftNamed(title).map(found -> new DraftedTemplate(found.id(), false)))
                .orElseGet(() -> new DraftedTemplate(
                        templates
                                .create(
                                        new TemplateDetails(
                                                title,
                                                description,
                                                null,
                                                "NORMAL",
                                                null,
                                                checklist == null ? List.of() : checklist),
                                        new TemplateMetadata(responsibleRole, null, null, null, null, null)
                                                .with(MetadataField.OUTPUT_KIND, outputKind),
                                        false)
                                .id(),
                        true)));
        return drafted[0];
    }
}
