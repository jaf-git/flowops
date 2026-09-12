package com.flowops.task.domain.model;

import com.flowops.task.domain.exception.CommentBodyRequiredException;
import java.time.Instant;
import java.util.Objects;

public record TaskComment(TaskCommentId id, TaskId task, PersonId author, String body, Instant writtenAt) {
    public TaskComment {
        Objects.requireNonNull(id);
        Objects.requireNonNull(task, "a comment is about a task");
        Objects.requireNonNull(author, "a comment was written by somebody");
        Objects.requireNonNull(writtenAt);

        String trimmed = body == null ? "" : body.trim();
        if (trimmed.isEmpty()) {
            throw new CommentBodyRequiredException();
        }
        body = trimmed;
    }

    public static TaskComment written(TaskId task, PersonId author, String body, Instant at) {
        return new TaskComment(TaskCommentId.generate(), task, author, body, at);
    }
}
