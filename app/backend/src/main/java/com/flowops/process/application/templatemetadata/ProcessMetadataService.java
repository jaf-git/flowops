package com.flowops.process.application.templatemetadata;

import com.flowops.process.application.shared.exception.TemplateNotFoundException;
import com.flowops.process.application.shared.port.LoadTemplatePort;
import com.flowops.process.application.shared.port.SaveTemplatePort;
import com.flowops.process.domain.model.ProcessMetadata;
import com.flowops.process.domain.model.ProcessTemplate;
import com.flowops.process.domain.model.TemplateId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessMetadataService implements ProcessMetadataUseCase {
    private final LoadTemplatePort loadTemplatePort;
    private final SaveTemplatePort saveTemplatePort;

    public ProcessMetadataService(LoadTemplatePort loadTemplatePort, SaveTemplatePort saveTemplatePort) {
        this.loadTemplatePort = loadTemplatePort;
        this.saveTemplatePort = saveTemplatePort;
    }

    @Override
    @Transactional
    public ProcessTemplate describe(TemplateId template, ProcessMetadata metadata) {
        ProcessTemplate found = loadTemplatePort.findById(template).orElseThrow(TemplateNotFoundException::new);

        ProcessTemplate described = found.describedBy(metadata);
        saveTemplatePort.replace(described);
        return described;
    }
}
