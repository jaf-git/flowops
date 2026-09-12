package com.flowops.process.application.definedependency;

import com.flowops.process.domain.model.ProcessTemplate;

public interface PromoteDependencyUseCase {
    ProcessTemplate execute(DefineDependencyCommand command);
}
