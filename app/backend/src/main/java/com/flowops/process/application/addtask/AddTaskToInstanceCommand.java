package com.flowops.process.application.addtask;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AddTaskToInstanceCommand(UUID instance, UUID task, NewTask newTask, List<UUID> waitsFor) {
    public record NewTask(String title, String description, UUID assignee, Instant deadline, String priority) {}

    public boolean isAttach() {
        return task != null;
    }
}
