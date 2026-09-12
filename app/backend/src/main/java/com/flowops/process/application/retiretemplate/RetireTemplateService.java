package com.flowops.process.application.retiretemplate;

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
import com.flowops.process.domain.model.TemplateId;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RetireTemplateService implements RetireTemplateUseCase {
    private static final String SEES_EVERY_INSTANCE = "PROCESS_VIEW_ANY";

    private final IdentifyCallerPort identifyCallerPort;
    private final CallerPermissionsPort callerPermissionsPort;
    private final LoadTemplatePort loadTemplatePort;
    private final SaveTemplatePort saveTemplatePort;
    private final AppendProcessEventPort appendProcessEventPort;
    private final Clock clock;

    public RetireTemplateService(
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
    public ProcessTemplate execute(TemplateId template) {
        Instant now = clock.instant();
        PersonId caller = identifyCallerPort
                .currentCaller()
                .orElseThrow(() -> new NotAuthenticatedException("there is no session behind this call"));
        ProcessTemplate found = loadTemplatePort.findById(template).orElseThrow(TemplateNotFoundException::new);

        if (!found.author().equals(caller) && !callerPermissionsPort.callerHolds(SEES_EVERY_INSTANCE)) {
            throw new NotTheAuthorException();
        }

        ProcessTemplate retired = found.retired();
        saveTemplatePort.retire(retired);
        appendProcessEventPort.append(ProcessEvent.onTemplate(template, ProcessAction.TEMPLATE_RETIRED, caller, now));
        return retired;
    }
}
