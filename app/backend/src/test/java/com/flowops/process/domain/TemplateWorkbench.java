package com.flowops.process.domain;

import com.flowops.process.domain.model.TaskTemplateRef;
import com.flowops.process.domain.model.TaskTemplateWork;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class TemplateWorkbench {
    private final Map<String, TaskTemplateRef> byTitle = new LinkedHashMap<>();
    private final Map<TaskTemplateRef, TaskTemplateWork> content = new LinkedHashMap<>();

    public TaskTemplateRef work(String title) {
        return byTitle.computeIfAbsent(title, name -> {
            TaskTemplateRef ref = TaskTemplateRef.of(UUID.randomUUID());
            content.put(ref, new TaskTemplateWork(name, "the description"));
            return ref;
        });
    }

    public Map<TaskTemplateRef, TaskTemplateWork> resolved() {
        return Map.copyOf(content);
    }

    public String titleOf(TaskTemplateRef ref) {
        TaskTemplateWork found = content.get(ref);
        return found == null ? null : found.title();
    }
}
