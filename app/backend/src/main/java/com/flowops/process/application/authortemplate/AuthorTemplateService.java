package com.flowops.process.application.authortemplate;

import com.flowops.process.application.shared.exception.NotAuthenticatedException;
import com.flowops.process.application.shared.exception.TemplateNameTakenException;
import com.flowops.process.application.shared.port.AppendProcessEventPort;
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
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorTemplateService implements AuthorTemplateUseCase {
    private final IdentifyCallerPort identifyCallerPort;
    private final LoadTemplatePort loadTemplatePort;
    private final SaveTemplatePort saveTemplatePort;
    private final AppendProcessEventPort appendProcessEventPort;
    private final Clock clock;

    public AuthorTemplateService(
            IdentifyCallerPort identifyCallerPort,
            LoadTemplatePort loadTemplatePort,
            SaveTemplatePort saveTemplatePort,
            AppendProcessEventPort appendProcessEventPort,
            Clock clock) {
        this.identifyCallerPort = identifyCallerPort;
        this.loadTemplatePort = loadTemplatePort;
        this.saveTemplatePort = saveTemplatePort;
        this.appendProcessEventPort = appendProcessEventPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ProcessTemplate execute(AuthorTemplateCommand command) {
        Instant now = clock.instant();
        PersonId author = identifyCallerPort
                .currentCaller()
                .orElseThrow(() -> new NotAuthenticatedException("there is no session behind this call"));

        if (command.name() != null
                && loadTemplatePort.activeNameExists(command.name().trim())) {
            throw new TemplateNameTakenException();
        }

        ProcessTemplate template = ProcessTemplate.authored(
                TemplateId.of(UUID.randomUUID()), command.name(), command.overview(), command.steps(), author, now);

        saveTemplatePort.create(template);
        appendProcessEventPort.append(
                ProcessEvent.onTemplate(template.id(), ProcessAction.TEMPLATE_AUTHORED, author, now));
        return template;
    }
}
