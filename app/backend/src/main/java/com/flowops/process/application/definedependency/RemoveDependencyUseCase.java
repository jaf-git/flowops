package com.flowops.process.application.definedependency;

import com.flowops.process.domain.model.ProcessTemplate;

public interface RemoveDependencyUseCase {
    ProcessTemplate execute(DefineDependencyCommand command);
}
