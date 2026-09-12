package com.flowops.process.infrastructure.notification;

import com.flowops.notification.application.published.NoticeRequest;
import com.flowops.notification.application.published.NotifyUseCase;
import com.flowops.process.application.shared.port.NotifyProcessPort;
import com.flowops.process.domain.model.InstanceId;
import com.flowops.process.domain.model.PersonId;
import com.flowops.process.domain.model.StepId;
import com.flowops.shared.notice.NotificationKind;
import com.flowops.shared.notice.SubjectRef;
import org.springframework.stereotype.Component;

@Component
public class RecordedProcessNotice implements NotifyProcessPort {
    private final NotifyUseCase notify;

    public RecordedProcessNotice(NotifyUseCase notify) {
        this.notify = notify;
    }

    @Override
    public void stepReachable(InstanceId instance, StepId step, PersonId processOwner) {
        notify.raise(new NoticeRequest(
                NotificationKind.STEP_REACHABLE, processOwner.value(), SubjectRef.step(step.value())));
    }

    @Override
    public void instanceComplete(InstanceId instance, PersonId processOwner) {
        notify.raise(new NoticeRequest(
                NotificationKind.RUN_COMPLETED, processOwner.value(), SubjectRef.run(instance.value())));
    }
}
