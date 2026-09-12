package com.flowops.process.application.instantiate;

import com.flowops.process.domain.model.PersonId;
import com.flowops.process.domain.model.TemplateId;

public record InstantiateCommand(TemplateId template, String name, PersonId processOwner) {}
