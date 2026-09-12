package com.flowops.task.application.createtask;

public interface CreateTaskUseCase {
    CreateTaskResult execute(CreateTaskCommand command);
}
