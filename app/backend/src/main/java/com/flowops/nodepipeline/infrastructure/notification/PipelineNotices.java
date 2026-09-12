package com.flowops.nodepipeline.infrastructure.notification;

import com.flowops.nodepipeline.application.port.NotifyPipelineFindingsPort;
import com.flowops.nodepipeline.domain.notify.PipelineMessage;
import com.flowops.notification.application.published.NoticeRequest;
import com.flowops.notification.application.published.NotifyUseCase;
import com.flowops.shared.notice.NotificationKind;
import com.flowops.shared.notice.SubjectRef;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PipelineNotices implements NotifyPipelineFindingsPort {
    private final NotifyUseCase notify;

    public PipelineNotices(NotifyUseCase notify) {
        this.notify = notify;
    }

    @Override
    public void tell(PipelineMessage message) {
        NotificationKind kind =
                switch (message.kind()) {
                    case WORK_COULD_HAVE_BEEN_FASTER,
                            WHICH_TEMPLATE_WAS_THIS,
                            A_HABIT_WORTH_A_TEMPLATE -> NotificationKind.A_TEMPLATE_EXISTS_FOR_THIS;
                    case STAFFING_NOTE, THE_ENGAGEMENT_HAS_STALLED, STEPS_THIS_ENGAGEMENT_SKIPPED -> NotificationKind
                            .AN_ENGAGEMENT_NEEDS_A_LOOK;
                    case AN_UNDOCUMENTED_PROCESS, WORK_WORTH_A_TEMPLATE -> NotificationKind
                            .SOMETHING_IS_WORTH_WRITING_DOWN;
                };

        UUID subject = UUID.fromString(message.subject());
        SubjectRef about = message.kind().forTheOwner() ? SubjectRef.workspace(subject) : SubjectRef.job(subject);

        notify.raise(new NoticeRequest(kind, message.to().person(), about));
    }
}
