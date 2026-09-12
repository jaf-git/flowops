package com.flowops.task.application.returntaskforrework;

import com.flowops.task.application.shared.TaskReviewSupport;
import com.flowops.task.application.shared.TaskTransitionResult;
import com.flowops.task.application.shared.port.NotifyTaskProgressPort;
import com.flowops.task.domain.model.TaskMove;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReturnTaskForReworkService implements ReturnTaskForReworkUseCase {
    private final TaskReviewSupport reviews;
    private final NotifyTaskProgressPort notifyTaskProgressPort;

    public ReturnTaskForReworkService(TaskReviewSupport reviews, NotifyTaskProgressPort notifyTaskProgressPort) {
        this.reviews = reviews;
        this.notifyTaskProgressPort = notifyTaskProgressPort;
    }

    @Override
    @Transactional
    public TaskTransitionResult execute(ReturnTaskForReworkCommand command) {
        TaskReviewSupport.InReview inReview = reviews.claim(command.task());

        TaskTransitionResult result = reviews.apply(TaskMove.returnedForRework(
                inReview.task(), inReview.openPhase(), inReview.reviewer(), command.reason(), inReview.now()));

        notifyTaskProgressPort.returnedForRework(inReview.task().id(), inReview.assignee(), command.reason());
        return result;
    }
}
