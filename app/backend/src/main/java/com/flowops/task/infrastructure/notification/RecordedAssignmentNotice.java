package com.flowops.task.infrastructure.notification;

import com.flowops.notification.application.published.NoticeRequest;
import com.flowops.notification.application.published.NotifyUseCase;
import com.flowops.shared.notice.NotificationKind;
import com.flowops.shared.notice.SubjectRef;
import com.flowops.task.application.shared.port.NotifyAssignmentPort;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.TaskId;
import org.springframework.stereotype.Component;

@Component
public class RecordedAssignmentNotice implements NotifyAssignmentPort {
    private final NotifyUseCase notify;

    public RecordedAssignmentNotice(NotifyUseCase notify) {
        this.notify = notify;
    }

    @Override
    public void assignmentGiven(TaskId task, PersonId assignee) {
        notify.raise(
                new NoticeRequest(NotificationKind.WORK_ASSIGNED, assignee.value(), SubjectRef.task(task.value())));
    }
}
