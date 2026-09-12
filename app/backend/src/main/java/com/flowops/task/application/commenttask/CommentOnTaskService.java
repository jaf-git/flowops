package com.flowops.task.application.commenttask;

import com.flowops.task.application.shared.TaskReviewSupport;
import com.flowops.task.application.shared.exception.NotAuthenticatedException;
import com.flowops.task.application.shared.exception.TaskNotFoundException;
import com.flowops.task.application.shared.exception.TaskOutOfScopeException;
import com.flowops.task.application.shared.port.AppendTaskEventPort;
import com.flowops.task.application.shared.port.IdentifyCallerPort;
import com.flowops.task.application.shared.port.LoadTaskPort;
import com.flowops.task.application.shared.port.NotifyTaskProgressPort;
import com.flowops.task.application.shared.port.TaskCommentPort;
import com.flowops.task.domain.enums.TaskState;
import com.flowops.task.domain.exception.TaskIsClosedException;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.Task;
import com.flowops.task.domain.model.TaskComment;
import com.flowops.task.domain.model.TaskId;
import com.flowops.task.domain.model.TaskMove;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentOnTaskService implements CommentOnTaskUseCase {
    private final IdentifyCallerPort identifyCallerPort;
    private final LoadTaskPort loadTaskPort;
    private final TaskReviewSupport visibility;
    private final TaskCommentPort comments;
    private final AppendTaskEventPort appendTaskEventPort;
    private final NotifyTaskProgressPort notifyTaskProgressPort;
    private final Clock clock;

    public CommentOnTaskService(
            IdentifyCallerPort identifyCallerPort,
            LoadTaskPort loadTaskPort,
            TaskReviewSupport visibility,
            TaskCommentPort comments,
            AppendTaskEventPort appendTaskEventPort,
            NotifyTaskProgressPort notifyTaskProgressPort,
            Clock clock) {
        this.identifyCallerPort = identifyCallerPort;
        this.loadTaskPort = loadTaskPort;
        this.visibility = visibility;
        this.comments = comments;
        this.appendTaskEventPort = appendTaskEventPort;
        this.notifyTaskProgressPort = notifyTaskProgressPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public TaskComment execute(TaskId task, String body) {
        Instant now = clock.instant();
        PersonId author = identifyCallerPort
                .currentCaller()
                .orElseThrow(() -> new NotAuthenticatedException("there is no session behind this call"));

        Task found = loadTaskPort.findById(task).orElseThrow(() -> new TaskNotFoundException("there is no such task"));
        if (!visibility.reaches(author, found)) {
            throw new TaskOutOfScopeException("this work belongs to somebody outside your team");
        }

        if (found.state() == TaskState.CLOSED) {
            throw new TaskIsClosedException();
        }

        TaskComment comment = TaskComment.written(task, author, body, now);
        comments.append(comment);
        appendTaskEventPort.append(TaskMove.commented(task, author, now));

        notifyTaskProgressPort.commented(task, author, found.assignee().orElse(null), found.creator());
        return comment;
    }
}
