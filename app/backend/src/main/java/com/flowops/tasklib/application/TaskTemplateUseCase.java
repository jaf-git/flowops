package com.flowops.tasklib.application;

import com.flowops.tasklib.application.port.TaskTemplatePort;
import com.flowops.tasklib.application.port.TemplatePerformancePort;
import com.flowops.tasklib.domain.TaskTemplate;
import com.flowops.tasklib.domain.TemplateDetails;
import com.flowops.tasklib.domain.TemplateMetadata;
import com.flowops.tasklib.domain.shape.ProcessShapeHint;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TaskTemplateUseCase {
    Library search(TaskTemplatePort.TemplateQuery query);

    List<TaskTemplate> awaitingApproval();

    List<String> typesInUse();

    TaskTemplate byId(UUID id);

    Performance performanceOf(UUID id);

    List<TemplatePerformancePort.Row> tasksIn(UUID id, String band);

    record Performance(
            TaskTemplate template,
            com.flowops.tasklib.domain.TemplatePerformance figures,
            com.flowops.tasklib.application.port.TemplatePerformancePort.LiveWork live) {}

    TaskTemplate create(TemplateDetails details, boolean submitForApproval);

    TaskTemplate create(TemplateDetails details, TemplateMetadata known, boolean submitForApproval);

    TaskTemplate edit(UUID id, TemplateDetails details, boolean submitForApproval);

    TaskTemplate approve(UUID id, TemplateDetails edited);

    List<TaskTemplate> approveAll(List<UUID> ids);

    TaskTemplate sendBack(UUID id, String reason);

    TaskTemplate retire(UUID id);

    TaskTemplate copy(UUID id);

    StampedTask stampTask(UUID templateId, StampRequest request);

    List<ProcessShapeHint> inspectShape(TemplateDetails details);

    List<TaskTemplatePort.Resemblance> resembling(String title, int limit);

    record Library(List<TaskTemplate> templates, int total, int page, int size) {
        public int totalPages() {
            return Math.max(1, (int) Math.ceil((double) total / size));
        }
    }

    record StampRequest(String title, String description, UUID assigneeId, Instant deadline, String priority) {}

    record StampedTask(UUID taskId, String title, UUID templateId) {}
}
