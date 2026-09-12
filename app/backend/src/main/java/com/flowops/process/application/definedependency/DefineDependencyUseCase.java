package com.flowops.process.application.definedependency;

import com.flowops.process.domain.model.ProcessTemplate;

public interface DefineDependencyUseCase {
    ProcessTemplate execute(DefineDependencyCommand command);
}
