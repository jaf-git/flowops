package com.flowops.process.application.authortemplate;

import com.flowops.process.domain.model.ProcessTemplate.StepDraft;
import java.util.List;

public record AuthorTemplateCommand(String name, String overview, List<StepDraft> steps) {}
