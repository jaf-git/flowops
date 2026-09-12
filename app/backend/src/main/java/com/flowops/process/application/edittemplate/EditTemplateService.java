package com.flowops.process.application.edittemplate;

import com.flowops.process.application.shared.exception.NotAuthenticatedException;
import com.flowops.process.application.shared.exception.NotTheAuthorException;
import com.flowops.process.application.shared.exception.TemplateNotFoundException;
import com.flowops.process.application.shared.port.AppendProcessEventPort;
import com.flowops.process.application.shared.port.CallerPermissionsPort;
import com.flowops.process.application.shared.port.IdentifyCallerPort;
import com.flowops.process.application.shared.port.LoadTemplatePort;
import com.flowops.process.application.shared.port.SaveTemplatePort;
import com.flowops.process.domain.enums.ProcessAction;
import com.flowops.process.domain.event.ProcessEvent;
import com.flowops.process.domain.model.PersonId;
import com.flowops.process.domain.model.ProcessTemplate;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EditTemplateService implements EditTemplateUseCase {
    private static final String SEES_EVERY_INSTANCE = "PROCESS_VIEW_ANY";

    private final IdentifyCallerPort identifyCallerPort;
    private final CallerPermissionsPort callerPermissionsPort;
    private final LoadTemplatePort loadTemplatePort;
    private final SaveTemplatePort saveTemplatePort;
    private final AppendProcessEventPort appendProcessEventPort;
    private final Clock clock;

    public EditTemplateService(
            IdentifyCallerPort identifyCallerPort,
            CallerPermissionsPort callerPermissionsPort,
            LoadTemplatePort loadTemplatePort,
            SaveTemplatePort saveTemplatePort,
            AppendProcessEventPort appendProcessEventPort,
            Clock clock) {
        this.identifyCallerPort = identifyCallerPort;
        this.callerPermissionsPort = callerPermissionsPort;
        this.loadTemplatePort = loadTemplatePort;
        this.saveTemplatePort = saveTemplatePort;
        this.appendProcessEventPort = appendProcessEventPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ProcessTemplate execute(EditTemplateCommand command) {
        Instant now = clock.instant();
        PersonId caller = identifyCallerPort
                .currentCaller()
                .orElseThrow(() -> new NotAuthenticatedException("there is no session behind this call"));
        ProcessTemplate template =
                loadTemplatePort.findById(command.template()).orElseThrow(TemplateNotFoundException::new);

        if (!template.author().equals(caller) && !callerPermissionsPort.callerHolds(SEES_EVERY_INSTANCE)) {
            throw new NotTheAuthorException();
        }

        ProcessTemplate edited = template.editedTo(command.overview(), command.steps());
        saveTemplatePort.replace(edited);
        appendProcessEventPort.append(
                ProcessEvent.onTemplate(template.id(), ProcessAction.TEMPLATE_EDITED, caller, now));
        return edited;
    }
}
