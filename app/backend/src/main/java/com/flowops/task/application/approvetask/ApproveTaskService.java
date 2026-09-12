package com.flowops.task.application.approvetask;

import com.flowops.task.application.shared.TaskReviewSupport;
import com.flowops.task.application.shared.TaskTransitionResult;
import com.flowops.task.application.shared.port.NotifyTaskProgressPort;
import com.flowops.task.application.shared.port.SaveApprovalPort;
import com.flowops.task.domain.model.Approval;
import com.flowops.task.domain.model.ApprovalScore;
import com.flowops.task.domain.model.TaskMove;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApproveTaskService implements ApproveTaskUseCase {
    private final TaskReviewSupport reviews;
    private final SaveApprovalPort saveApprovalPort;
    private final NotifyTaskProgressPort notifyTaskProgressPort;

    public ApproveTaskService(
            TaskReviewSupport reviews,
            SaveApprovalPort saveApprovalPort,
            NotifyTaskProgressPort notifyTaskProgressPort) {
        this.reviews = reviews;
        this.saveApprovalPort = saveApprovalPort;
        this.notifyTaskProgressPort = notifyTaskProgressPort;
    }

    @Override
    @Transactional
    public TaskTransitionResult execute(ApproveTaskCommand command) {
        TaskReviewSupport.InReview inReview = reviews.claim(command.task());

        ApprovalScore score = ApprovalScore.of(command.score());

        TaskTransitionResult result = reviews.apply(
                TaskMove.approved(inReview.task(), inReview.openPhase(), inReview.reviewer(), inReview.now()));
        saveApprovalPort.save(
                Approval.of(inReview.task().id(), score, command.comment(), inReview.reviewer(), inReview.now()));

        notifyTaskProgressPort.approved(inReview.task().id(), inReview.assignee());
        return result;
    }
}
