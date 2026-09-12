package com.flowops.task.infrastructure.notification;

import com.flowops.notification.application.published.NoticeRequest;
import com.flowops.notification.application.published.NotifyUseCase;
import com.flowops.shared.notice.NotificationKind;
import com.flowops.shared.notice.SubjectRef;
import com.flowops.task.application.shared.port.NotifyNegotiationPort;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.TaskId;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RecordedNegotiationNotice implements NotifyNegotiationPort {
    private static final Logger LOG = LoggerFactory.getLogger(RecordedNegotiationNotice.class);

    private final NotifyUseCase notify;

    public RecordedNegotiationNotice(NotifyUseCase notify) {
        this.notify = notify;
    }

    @Override
    public void rejected(TaskId task, PersonId assigner, String reason) {
        notify.raise(
                new NoticeRequest(NotificationKind.WORK_REJECTED, assigner.value(), SubjectRef.task(task.value())));
    }

    @Override
    public void deadlineProposed(TaskId task, PersonId assigner, Instant proposedDeadline, String reason) {
        notify.raise(
                new NoticeRequest(NotificationKind.DEADLINE_PROPOSED, assigner.value(), SubjectRef.task(task.value())));
    }

    @Override
    public void deadlineProposalAccepted(TaskId task, PersonId assignee, Instant newDeadline) {
        notify.raise(
                new NoticeRequest(NotificationKind.DEADLINE_DECIDED, assignee.value(), SubjectRef.task(task.value())));
    }

    @Override
    public void deadlineProposalDeclined(TaskId task, PersonId assignee, String reason) {
        notify.raise(
                new NoticeRequest(NotificationKind.DEADLINE_DECIDED, assignee.value(), SubjectRef.task(task.value())));
    }

    @Override
    public void deadlineChanged(TaskId task, PersonId assignee, Instant formerDeadline, Instant newDeadline) {
        LOG.info(
                "deadline notice owed: task {} moved from {} to {} and person {} has not been told; no kind in"
                        + " NOTIFICATION_05 covers a date moved without a proposal",
                task.value(),
                formerDeadline,
                newDeadline,
                assignee.value());
    }
}
