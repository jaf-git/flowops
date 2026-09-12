package com.flowops.process.api.mapper;

import com.flowops.process.api.dto.DependencyResponse;
import com.flowops.process.api.dto.ProcessMetadataResponse;
import com.flowops.process.api.dto.StepRequest;
import com.flowops.process.api.dto.StepResponse;
import com.flowops.process.api.dto.TemplateListResponse;
import com.flowops.process.api.dto.TemplateResponse;
import com.flowops.process.api.dto.TemplateSummaryResponse;
import com.flowops.process.application.shared.port.TaskTemplateContentPort;
import com.flowops.process.domain.model.Applicability;
import com.flowops.process.domain.model.ProcessTemplate;
import com.flowops.process.domain.model.ProcessTemplate.StepDraft;
import com.flowops.process.domain.model.StepDefinition;
import com.flowops.process.domain.model.StepDependency;
import com.flowops.process.domain.model.StepId;
import com.flowops.process.domain.model.TaskTemplateRef;
import com.flowops.process.domain.model.TaskTemplateWork;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ProcessDtoMapper {
    private final TaskTemplateContentPort taskTemplateContentPort;

    public ProcessDtoMapper(TaskTemplateContentPort taskTemplateContentPort) {
        this.taskTemplateContentPort = taskTemplateContentPort;
    }

    public List<StepDraft> toDrafts(List<StepRequest> steps) {
        List<StepDraft> drafts = new ArrayList<>();
        if (steps == null) {
            return drafts;
        }
        for (StepRequest step : steps) {
            drafts.add(new StepDraft(
                    step.id() == null ? null : StepId.of(step.id()),
                    step.taskTemplateId() == null ? null : TaskTemplateRef.of(step.taskTemplateId()),
                    step.expectedDurationHours(),
                    step.optional() ? Applicability.when(step.conditionNote()) : Applicability.always()));
        }
        return drafts;
    }

    public TemplateResponse toResponse(ProcessTemplate template) {
        Map<TaskTemplateRef, TaskTemplateWork> work = taskTemplateContentPort.namesFor(
                template.steps().stream().map(StepDefinition::taskTemplateId).toList());
        List<StepResponse> steps = new ArrayList<>();
        for (StepDefinition step : template.steps()) {
            TaskTemplateWork words = work.get(step.taskTemplateId());
            steps.add(new StepResponse(
                    step.id().value(),
                    step.taskTemplateId().value(),
                    words == null ? null : words.title(),
                    words == null ? null : words.description(),
                    step.expectedDurationHours(),
                    step.position(),
                    step.applicability().optional(),
                    step.applicability().conditionNote()));
        }
        List<DependencyResponse> edges = new ArrayList<>();
        for (StepDependency edge : template.dependencies()) {
            edges.add(new DependencyResponse(
                    edge.dependent().value(), edge.dependsOn().value()));
        }
        return new TemplateResponse(
                template.id().value(),
                template.name(),
                template.overview(),
                template.active(),
                template.author().value(),
                template.createdAt(),
                steps,
                edges,
                ProcessMetadataResponse.of(template.metadata()));
    }

    public TemplateListResponse toList(List<ProcessTemplate> templates) {
        List<TemplateSummaryResponse> summaries = new ArrayList<>();
        for (ProcessTemplate template : templates) {
            summaries.add(new TemplateSummaryResponse(
                    template.id().value(),
                    template.name(),
                    template.overview(),
                    template.steps().size(),
                    template.active()));
        }
        return new TemplateListResponse(summaries);
    }
}
