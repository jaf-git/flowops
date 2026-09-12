package com.flowops.task.application.closetask;

import com.flowops.task.application.shared.TaskReviewSupport;
import com.flowops.task.application.shared.TaskTransitionResult;
import com.flowops.task.domain.model.TaskMove;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CloseTaskService implements CloseTaskUseCase {
    private final TaskReviewSupport reviews;

    public CloseTaskService(TaskReviewSupport reviews) {
        this.reviews = reviews;
    }

    @Override
    @Transactional
    public TaskTransitionResult execute(CloseTaskCommand command) {
        TaskReviewSupport.InReview inReview = reviews.claim(command.task());
        return reviews.apply(
                TaskMove.closed(inReview.task(), inReview.openPhase(), inReview.reviewer(), inReview.now()));
    }
}
