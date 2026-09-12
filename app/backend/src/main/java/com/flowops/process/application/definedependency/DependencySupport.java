package com.flowops.process.application.definedependency;

import com.flowops.process.application.shared.exception.NotAuthenticatedException;
import com.flowops.process.application.shared.exception.TemplateNotFoundException;
import com.flowops.process.application.shared.port.IdentifyCallerPort;
import com.flowops.process.application.shared.port.LoadTemplatePort;
import com.flowops.process.domain.exception.CrossTemplateEdgeException;
import com.flowops.process.domain.exception.UnknownStepException;
import com.flowops.process.domain.model.PersonId;
import com.flowops.process.domain.model.ProcessTemplate;
import com.flowops.process.domain.model.StepId;
import com.flowops.process.domain.model.TemplateId;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class DependencySupport {
    private final IdentifyCallerPort identifyCallerPort;
    private final LoadTemplatePort loadTemplatePort;

    public DependencySupport(IdentifyCallerPort identifyCallerPort, LoadTemplatePort loadTemplatePort) {
        this.identifyCallerPort = identifyCallerPort;
        this.loadTemplatePort = loadTemplatePort;
    }

    public PersonId caller() {
        return identifyCallerPort
                .currentCaller()
                .orElseThrow(() -> new NotAuthenticatedException("there is no session behind this call"));
    }

    public ProcessTemplate load(TemplateId id) {
        return loadTemplatePort.findById(id).orElseThrow(TemplateNotFoundException::new);
    }

    public void requireBothStepsBelongTo(ProcessTemplate template, StepId dependent, StepId dependsOn) {
        requireBelongs(template, dependent);
        requireBelongs(template, dependsOn);
    }

    private void requireBelongs(ProcessTemplate template, StepId step) {
        boolean mine =
                template.steps().stream().anyMatch(defined -> defined.id().equals(step));
        if (mine) {
            return;
        }
        Optional<TemplateId> elsewhere = loadTemplatePort.templateOf(step);
        if (elsewhere.isPresent()) {
            throw new CrossTemplateEdgeException();
        }
        throw new UnknownStepException();
    }
}
