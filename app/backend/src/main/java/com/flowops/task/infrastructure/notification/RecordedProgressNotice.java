package com.flowops.task.infrastructure.notification;

import com.flowops.notification.application.published.NoticeRequest;
import com.flowops.notification.application.published.NotifyUseCase;
import com.flowops.shared.notice.NotificationKind;
import com.flowops.shared.notice.SubjectRef;
import com.flowops.task.application.shared.port.NotifyTaskProgressPort;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.TaskId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RecordedProgressNotice implements NotifyTaskProgressPort {
    private static final Logger LOG = LoggerFactory.getLogger(RecordedProgressNotice.class);

    private final NotifyUseCase notify;

    public RecordedProgressNotice(NotifyUseCase notify) {
        this.notify = notify;
    }

    @Override
    public void blockRaised(TaskId task, PersonId assignee, String reason) {
        LOG.info(
                "block raised on task {} by person {}; no notice yet -- a blocker becomes LONG_BLOCK_1 once it"
                        + " outlives the workspace threshold, which AUTOMATION raises",
                task.value(),
                assignee.value());
    }

    @Override
    public void completionSubmitted(TaskId task, PersonId assignee) {
        LOG.info(
                "task {} was submitted by person {}; no notice yet -- work waiting in review becomes STALE_REVIEW"
                        + " past the threshold, and that one points at the reviewer",
                task.value(),
                assignee.value());
    }

    @Override
    public void approved(TaskId task, PersonId assignee) {
        notify.raise(
                new NoticeRequest(NotificationKind.WORK_APPROVED, assignee.value(), SubjectRef.task(task.value())));
    }

    @Override
    public void returnedForRework(TaskId task, PersonId assignee, String reason) {
        notify.raise(
                new NoticeRequest(NotificationKind.WORK_RETURNED, assignee.value(), SubjectRef.task(task.value())));
    }

    @Override
    public void reassigned(TaskId task, PersonId previousAssignee, PersonId newAssignee, String reason) {
        notify.raise(
                new NoticeRequest(NotificationKind.WORK_ASSIGNED, newAssignee.value(), SubjectRef.task(task.value())));
    }

    @Override
    public void overridden(TaskId task, PersonId assignee, PersonId creator, String target, String reason) {
        LOG.info(
                "override notice owed: task {} was forced to {} by the owner; person {} holds it and person {} gave"
                        + " it out. Two recipients, and NOTIFICATION_05 carries no row for either",
                task.value(),
                target,
                assignee == null ? "nobody" : assignee.value(),
                creator.value());
    }

    @Override
    public void commented(TaskId task, PersonId author, PersonId assignee, PersonId creator) {
        LOG.info(
                "comment notice owed: person {} wrote on task {}; person {} holds it and person {} gave it out."
                        + " Two recipients, and NOTIFICATION_05 carries no row for either",
                author.value(),
                task.value(),
                assignee == null ? "nobody" : assignee.value(),
                creator.value());
    }
}
