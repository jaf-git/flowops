package com.flowops.chat.domain.exception;

import java.util.UUID;

public class MessageAlreadyConvertedException extends RuntimeException {
    private final UUID taskId;

    public MessageAlreadyConvertedException(UUID taskId) {
        super("this message has already become a task");
        this.taskId = taskId;
    }

    public UUID taskId() {
        return taskId;
    }
}
