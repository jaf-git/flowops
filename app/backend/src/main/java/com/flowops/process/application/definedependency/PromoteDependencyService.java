package com.flowops.process.application.definedependency;

import com.flowops.process.application.shared.port.AppendProcessEventPort;
import com.flowops.process.application.shared.port.SaveTemplatePort;
import com.flowops.process.domain.enums.ProcessAction;
import com.flowops.process.domain.event.ProcessEvent;
import com.flowops.process.domain.model.PersonId;
import com.flowops.process.domain.model.ProcessTemplate;
import com.flowops.process.domain.model.StepDependency;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromoteDependencyService implements PromoteDependencyUseCase {
    private final DependencySupport support;
    private final SaveTemplatePort saveTemplatePort;
    private final AppendProcessEventPort appendProcessEventPort;
    private final Clock clock;

    public PromoteDependencyService(
            DependencySupport support,
            SaveTemplatePort saveTemplatePort,
            AppendProcessEventPort appendProcessEventPort,
            Clock clock) {
        this.support = support;
        this.saveTemplatePort = saveTemplatePort;
        this.appendProcessEventPort = appendProcessEventPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ProcessTemplate execute(DefineDependencyCommand command) {
        Instant now = clock.instant();
        PersonId caller = support.caller();
        ProcessTemplate template = support.load(command.template());
        support.requireBothStepsBelongTo(template, command.dependent(), command.dependsOn());

        if (template.alreadyDependsOn(command.dependent(), command.dependsOn())) {
            return template;
        }

        ProcessTemplate widened = template.dependingOn(command.dependent(), command.dependsOn());

        boolean promoted = saveTemplatePort.promoteDependency(
                template.id(), new StepDependency(command.dependent(), command.dependsOn()), caller, now);
        if (!promoted) {
            return template;
        }

        appendProcessEventPort.append(
                ProcessEvent.onTemplate(template.id(), ProcessAction.DEPENDENCY_DEFINED, caller, now));
        return widened;
    }
}
