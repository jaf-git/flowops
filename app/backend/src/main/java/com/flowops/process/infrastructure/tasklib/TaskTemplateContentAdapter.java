package com.flowops.process.infrastructure.tasklib;

import com.flowops.process.application.shared.port.TaskTemplateContentPort;
import com.flowops.process.domain.model.TaskTemplateRef;
import com.flowops.process.domain.model.TaskTemplateWork;
import com.flowops.tasklib.application.published.TemplateContentUseCase;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TaskTemplateContentAdapter implements TaskTemplateContentPort {
    private final TemplateContentUseCase content;

    public TaskTemplateContentAdapter(TemplateContentUseCase content) {
        this.content = content;
    }

    @Override
    public Map<TaskTemplateRef, TaskTemplateWork> contentOf(Collection<TaskTemplateRef> templates) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (TaskTemplateRef ref : templates) {
            ids.add(ref.value());
        }

        Map<TaskTemplateRef, TaskTemplateWork> resolved = new LinkedHashMap<>();
        content.ofAll(ids)
                .forEach((id, found) ->
                        resolved.put(TaskTemplateRef.of(id), new TaskTemplateWork(found.title(), found.description())));
        return resolved;
    }

    @Override
    public Map<TaskTemplateRef, TaskTemplateWork> namesFor(Collection<TaskTemplateRef> templates) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (TaskTemplateRef ref : templates) {
            ids.add(ref.value());
        }

        Map<TaskTemplateRef, TaskTemplateWork> resolved = new LinkedHashMap<>();
        content.wordsForDisplay(ids)
                .forEach((id, found) ->
                        resolved.put(TaskTemplateRef.of(id), new TaskTemplateWork(found.title(), found.description())));
        return resolved;
    }
}
