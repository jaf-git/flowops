package com.flowops.task.application.shared;

import com.flowops.task.application.shared.port.CallerPermissionsPort;
import com.flowops.task.application.shared.port.ReportingLinePort;
import com.flowops.task.domain.model.PersonId;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class TaskAudienceRule {
    private static final String VIEW_ANY = "TASK_VIEW_ANY";
    private static final String VIEW_SUBTREE = "TASK_VIEW_SUBTREE";

    private final CallerPermissionsPort callerPermissionsPort;
    private final ReportingLinePort reportingLinePort;

    public TaskAudienceRule(CallerPermissionsPort callerPermissionsPort, ReportingLinePort reportingLinePort) {
        this.callerPermissionsPort = callerPermissionsPort;
        this.reportingLinePort = reportingLinePort;
    }

    public TaskAudience forCaller(PersonId caller) {
        if (callerPermissionsPort.callerHolds(VIEW_ANY)) {
            return TaskAudience.everything();
        }

        Set<PersonId> visible = new LinkedHashSet<>();
        visible.add(caller);
        if (callerPermissionsPort.callerHolds(VIEW_SUBTREE)) {
            visible.addAll(reportingLinePort.subtreeOf(caller));
        }
        return TaskAudience.of(visible, caller);
    }
}
