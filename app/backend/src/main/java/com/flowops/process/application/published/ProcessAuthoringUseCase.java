package com.flowops.process.application.published;

import java.util.List;
import java.util.UUID;

public interface ProcessAuthoringUseCase {
    UUID authorTemplate(String name, String overview, List<StepSpecification> steps);

    UUID authorComposedDraft(String name, String overview, List<StepSpecification> steps);

    void appendSteps(UUID templateId, List<StepSpecification> steps);

    void insertStep(UUID templateId, int position, StepSpecification step);

    void addDependency(UUID templateId, String dependentTitle, String dependsOnTitle, String kind, Double confidence);

    void removeDependency(UUID templateId, String dependentTitle, String dependsOnTitle);

    record StepSpecification(UUID taskTemplateId, Integer expectedDurationHours) {}
}
