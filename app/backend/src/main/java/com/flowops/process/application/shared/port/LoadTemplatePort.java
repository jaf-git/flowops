package com.flowops.process.application.shared.port;

import com.flowops.process.domain.model.ProcessTemplate;
import com.flowops.process.domain.model.StepId;
import com.flowops.process.domain.model.TemplateId;
import java.util.List;
import java.util.Optional;

public interface LoadTemplatePort {
    Optional<ProcessTemplate> findById(TemplateId id);

    List<ProcessTemplate> findAllActive();

    boolean activeNameExists(String name);

    Optional<TemplateId> templateOf(StepId step);
}
