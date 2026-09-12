package com.flowops.task.infrastructure.persistence;

import com.flowops.task.application.shared.port.TaskCommentPort;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.TaskComment;
import com.flowops.task.domain.model.TaskCommentId;
import com.flowops.task.domain.model.TaskId;
import com.flowops.task.infrastructure.persistence.entity.TaskCommentJpaEntity;
import com.flowops.task.infrastructure.persistence.repository.TaskCommentJpaRepository;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CommentPersistenceAdapter implements TaskCommentPort {
    private final TaskCommentJpaRepository comments;

    public CommentPersistenceAdapter(TaskCommentJpaRepository comments) {
        this.comments = comments;
    }

    @Override
    public void append(TaskComment comment) {
        comments.save(new TaskCommentJpaEntity(
                comment.id().value(),
                comment.task().value(),
                comment.author().value(),
                comment.body(),
                comment.writtenAt()));
    }

    @Override
    public List<TaskComment> allOf(TaskId task) {
        return comments.findByTaskIdOrderByCreatedAtAsc(task.value()).stream()
                .map(row -> new TaskComment(
                        TaskCommentId.of(row.getId()),
                        TaskId.of(row.getTaskId()),
                        PersonId.of(row.getAuthorUserId()),
                        row.getBody(),
                        row.getCreatedAt()))
                .toList();
    }
}
