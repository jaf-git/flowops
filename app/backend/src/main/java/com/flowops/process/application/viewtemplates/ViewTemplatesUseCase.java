package com.flowops.process.application.viewtemplates;

import com.flowops.process.domain.model.ProcessTemplate;
import com.flowops.process.domain.model.TemplateId;
import java.util.List;

public interface ViewTemplatesUseCase {
    List<ProcessTemplate> all();

    ProcessTemplate one(TemplateId id);
}
