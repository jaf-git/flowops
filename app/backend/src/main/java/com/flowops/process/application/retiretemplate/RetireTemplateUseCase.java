package com.flowops.process.application.retiretemplate;

import com.flowops.process.domain.model.ProcessTemplate;
import com.flowops.process.domain.model.TemplateId;

public interface RetireTemplateUseCase {
    ProcessTemplate execute(TemplateId template);
}
