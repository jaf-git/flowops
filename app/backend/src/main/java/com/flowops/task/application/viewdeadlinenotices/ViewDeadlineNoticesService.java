package com.flowops.task.application.viewdeadlinenotices;

import com.flowops.task.application.shared.exception.NotAuthenticatedException;
import com.flowops.task.application.shared.port.IdentifyCallerPort;
import com.flowops.task.application.shared.port.LoadDeadlineNoticesPort;
import com.flowops.task.application.shared.port.LoadPersonPort;
import com.flowops.task.domain.model.PersonId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViewDeadlineNoticesService implements ViewDeadlineNoticesUseCase {
    private final IdentifyCallerPort identifyCallerPort;
    private final LoadDeadlineNoticesPort loadDeadlineNoticesPort;
    private final LoadPersonPort loadPersonPort;

    public ViewDeadlineNoticesService(
            IdentifyCallerPort identifyCallerPort,
            LoadDeadlineNoticesPort loadDeadlineNoticesPort,
            LoadPersonPort loadPersonPort) {
        this.identifyCallerPort = identifyCallerPort;
        this.loadDeadlineNoticesPort = loadDeadlineNoticesPort;
        this.loadPersonPort = loadPersonPort;
    }

    @Override
    @Transactional(readOnly = true)
    public ViewDeadlineNoticesResult execute() {
        PersonId caller = identifyCallerPort
                .currentCaller()
                .orElseThrow(() -> new NotAuthenticatedException("there is no session behind this call"));

        return new ViewDeadlineNoticesResult(loadDeadlineNoticesPort.unacknowledgedFor(caller).stream()
                .map(notice -> new ViewDeadlineNoticesResult.Notice(
                        notice.task(),
                        notice.title(),
                        notice.assignee(),
                        loadPersonPort
                                .describe(notice.assignee())
                                .map(LoadPersonPort.Person::displayName)
                                .orElse(""),
                        notice.deadline(),
                        notice.setAt()))
                .toList());
    }
}
