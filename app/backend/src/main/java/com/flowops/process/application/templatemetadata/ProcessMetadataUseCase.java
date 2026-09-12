package com.flowops.process.application.templatemetadata;

import com.flowops.process.domain.model.ProcessMetadata;
import com.flowops.process.domain.model.ProcessTemplate;
import com.flowops.process.domain.model.TemplateId;

public interface ProcessMetadataUseCase {
    ProcessTemplate describe(TemplateId template, ProcessMetadata metadata);
}
