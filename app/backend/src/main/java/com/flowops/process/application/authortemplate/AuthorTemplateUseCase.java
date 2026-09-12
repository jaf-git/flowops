package com.flowops.process.application.authortemplate;

import com.flowops.process.domain.model.ProcessTemplate;

public interface AuthorTemplateUseCase {
    ProcessTemplate execute(AuthorTemplateCommand command);
}
