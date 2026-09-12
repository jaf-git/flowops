package com.flowops.discovery.infrastructure.notification;

import com.flowops.discovery.application.shared.port.DiscoveryNoticePort;
import com.flowops.discovery.application.shared.port.PersonRolePort;
import com.flowops.discovery.domain.model.BracketId;
import com.flowops.discovery.domain.model.JobId;
import com.flowops.notification.application.published.NoticeRequest;
import com.flowops.notification.application.published.NotifyUseCase;
import com.flowops.shared.notice.NotificationKind;
import com.flowops.shared.notice.SubjectRef;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RecordedDiscoveryNotice implements DiscoveryNoticePort {
    private final NotifyUseCase notify;
    private final PersonRolePort people;

    public RecordedDiscoveryNotice(NotifyUseCase notify, PersonRolePort people) {
        this.notify = notify;
        this.people = people;
    }

    @Override
    public void awaitedWorkArrived(UUID waiter, BracketId waitersBracket) {
        tell(waiter, NotificationKind.AWAITED_WORK_ARRIVED, SubjectRef.bracket(waitersBracket.value()));
    }

    @Override
    public void awaitedWorkDied(UUID waiter, BracketId waitersBracket) {
        tell(waiter, NotificationKind.AWAITED_WORK_DROPPED, SubjectRef.bracket(waitersBracket.value()));
    }

    @Override
    public void stillGoing(UUID closureHolder, BracketId bracket) {
        tell(closureHolder, NotificationKind.BRACKET_STILL_GOING, SubjectRef.bracket(bracket.value()));
    }

    @Override
    public void jobForceClosed(UUID holder, JobId job) {
        tell(holder, NotificationKind.JOB_FORCE_CLOSED, SubjectRef.job(job.value()));
    }

    @Override
    public void somebodyIsWaitingOnYou(UUID closureHolder, BracketId theirBracket) {
        tell(closureHolder, NotificationKind.SOMEBODY_IS_WAITING_ON_YOU, SubjectRef.bracket(theirBracket.value()));
    }

    @Override
    public void theDateYouExpectedHasPassed(UUID waiter, BracketId waitersBracket) {
        tell(waiter, NotificationKind.WAIT_DATE_HAS_PASSED, SubjectRef.bracket(waitersBracket.value()));
    }

    @Override
    public void thisHasBeenWaitingALongTime(UUID jobOwner, JobId job) {
        tell(jobOwner, NotificationKind.EXTERNAL_WAIT_IS_LONG, SubjectRef.job(job.value()));
    }

    private void tell(UUID recipient, NotificationKind kind, SubjectRef subject) {
        if (!people.isActiveMember(recipient)) {
            return;
        }
        notify.raise(new NoticeRequest(kind, recipient, subject));
    }
}
