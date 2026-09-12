package com.flowops.task.application.acknowledgedeadlinenotice;

import com.flowops.task.application.shared.TaskAuthorshipSupport;
import com.flowops.task.application.shared.exception.NotTheCreatorException;
import com.flowops.task.application.shared.port.SaveTaskPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcknowledgeDeadlineNoticeService implements AcknowledgeDeadlineNoticeUseCase {
    private final TaskAuthorshipSupport authorship;
    private final SaveTaskPort saveTaskPort;

    public AcknowledgeDeadlineNoticeService(TaskAuthorshipSupport authorship, SaveTaskPort saveTaskPort) {
        this.authorship = authorship;
        this.saveTaskPort = saveTaskPort;
    }

    @Override
    @Transactional
    public void execute(AcknowledgeDeadlineNoticeCommand command) {
        TaskAuthorshipSupport.InHand inHand = authorship.claim(command.task());

        if (!inHand.task().wasCreatedBy(inHand.actor())) {
            throw new NotTheCreatorException("only the person who gave this work out can say they have seen it");
        }

        saveTaskPort.acknowledgeDeadline(inHand.task().id(), inHand.now());
    }
}
