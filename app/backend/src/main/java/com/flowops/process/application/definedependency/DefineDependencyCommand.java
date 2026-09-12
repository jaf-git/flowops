package com.flowops.process.application.definedependency;

import com.flowops.process.domain.model.StepId;
import com.flowops.process.domain.model.TemplateId;

public record DefineDependencyCommand(TemplateId template, StepId dependent, StepId dependsOn) {}
