package com.flowops.process.application.edittemplate;

import com.flowops.process.domain.model.ProcessTemplate.StepDraft;
import com.flowops.process.domain.model.TemplateId;
import java.util.List;

public record EditTemplateCommand(TemplateId template, String overview, List<StepDraft> steps) {}
