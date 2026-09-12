package com.flowops.process.application.viewtemplates;

import com.flowops.process.application.shared.exception.TemplateNotFoundException;
import com.flowops.process.application.shared.port.LoadTemplatePort;
import com.flowops.process.domain.model.ProcessTemplate;
import com.flowops.process.domain.model.TemplateId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViewTemplatesService implements ViewTemplatesUseCase {
    private final LoadTemplatePort loadTemplatePort;

    public ViewTemplatesService(LoadTemplatePort loadTemplatePort) {
        this.loadTemplatePort = loadTemplatePort;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProcessTemplate> all() {
        return loadTemplatePort.findAllActive();
    }

    @Override
    @Transactional(readOnly = true)
    public ProcessTemplate one(TemplateId id) {
        return loadTemplatePort.findById(id).orElseThrow(TemplateNotFoundException::new);
    }
}
