package com.flowops.process.application.edittemplate;

import com.flowops.process.domain.model.ProcessTemplate;

public interface EditTemplateUseCase {
    ProcessTemplate execute(EditTemplateCommand command);
}
