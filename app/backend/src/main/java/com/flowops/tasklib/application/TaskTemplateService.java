package com.flowops.tasklib.application;

import com.flowops.tasklib.application.exception.NotTheAuthorException;
import com.flowops.tasklib.application.exception.TemplateNotFoundException;
import com.flowops.tasklib.application.port.CreateTaskFromTemplatePort;
import com.flowops.tasklib.application.port.IdentifyCallerPort;
import com.flowops.tasklib.application.port.TaskTemplatePort;
import com.flowops.tasklib.application.port.TemplatePerformancePort;
import com.flowops.tasklib.domain.TaskTemplate;
import com.flowops.tasklib.domain.TemplateDetails;
import com.flowops.tasklib.domain.TemplateMetadata;
import com.flowops.tasklib.domain.shape.ProcessShapeHeuristic;
import com.flowops.tasklib.domain.shape.ProcessShapeHint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskTemplateService implements TaskTemplateUseCase {
    private final TaskTemplatePort templates;
    private final IdentifyCallerPort caller;
    private final CreateTaskFromTemplatePort tasks;
    private final TemplatePerformancePort performance;
    private final java.time.Clock clock;

    private final ProcessShapeHeuristic shapeHeuristic = ProcessShapeHeuristic.standard();

    public TaskTemplateService(
            TaskTemplatePort templates,
            IdentifyCallerPort caller,
            CreateTaskFromTemplatePort tasks,
            TemplatePerformancePort performance,
            java.time.Clock clock) {
        this.templates = templates;
        this.caller = caller;
        this.tasks = tasks;
        this.performance = performance;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Library search(TaskTemplatePort.TemplateQuery query) {
        UUID viewer = requireCaller();
        return new Library(templates.search(query, viewer), templates.count(query, viewer), query.page(), query.size());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskTemplate> awaitingApproval() {
        return templates.awaitingApproval();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> typesInUse() {
        return templates.typesInUse();
    }

    @Override
    @Transactional(readOnly = true)
    public TaskTemplate byId(UUID id) {
        return load(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Performance performanceOf(UUID id) {
        TaskTemplate template = load(id);
        return new Performance(template, performance.performanceOf(id), performance.liveWorkFor(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TemplatePerformancePort.Row> tasksIn(UUID id, String band) {
        load(id);
        return performance.tasksIn(id, band);
    }

    @Override
    @Transactional
    public TaskTemplate create(TemplateDetails details, boolean submitForApproval) {
        return persist(TaskTemplate.written(details, requireCaller(), submitForApproval, clock.instant()));
    }

    @Override
    @Transactional
    public TaskTemplate create(TemplateDetails details, TemplateMetadata known, boolean submitForApproval) {
        return persist(TaskTemplate.written(details, known, requireCaller(), submitForApproval, clock.instant()));
    }

    @Override
    @Transactional
    public TaskTemplate edit(UUID id, TemplateDetails details, boolean submitForApproval) {
        TaskTemplate existing = load(id);

        if (!existing.belongsTo(requireCaller())) {
            throw new NotTheAuthorException("this template belongs to somebody else");
        }
        Instant now = clock.instant();
        TaskTemplate revised = existing.revisedTo(details, now);

        return persist(submitForApproval && revised.isDraft() ? revised.submitted(now) : revised);
    }

    @Override
    @Transactional
    public TaskTemplate approve(UUID id, TemplateDetails edited) {
        TaskTemplate proposal = load(id);
        TaskTemplate reviewed = edited == null ? proposal : proposal.revisedTo(edited, clock.instant());
        return persist(reviewed.approved(clock.instant()));
    }

    @Override
    @Transactional
    public List<TaskTemplate> approveAll(List<UUID> ids) {
        List<TaskTemplate> approved = new ArrayList<>();
        for (UUID id : ids) {
            approved.add(approve(id, null));
        }
        return approved;
    }

    @Override
    @Transactional
    public TaskTemplate sendBack(UUID id, String reason) {
        return persist(load(id).sentBack(reason, clock.instant()));
    }

    @Override
    @Transactional
    public TaskTemplate retire(UUID id) {
        return persist(load(id).retired(clock.instant()));
    }

    @Override
    @Transactional
    public TaskTemplate copy(UUID id) {
        return create(load(id).details(), false);
    }

    @Override
    @Transactional
    public StampedTask stampTask(UUID templateId, StampRequest request) {
        TaskTemplate template = load(templateId);

        template.requireUsable();

        TemplateDetails details = template.details();
        String title = blankTo(request.title(), details.title());
        UUID taskId = tasks.createFromTemplate(new CreateTaskFromTemplatePort.NewTask(
                title,
                blankTo(request.description(), details.description()),
                request.assigneeId(),
                request.deadline(),
                blankTo(request.priority(), details.priority()),
                templateId,
                details.estimatedHours()));

        templates.recordUse(templateId);

        return new StampedTask(taskId, title, templateId);
    }

    private static String blankTo(String given, String fallback) {
        return given == null || given.isBlank() ? fallback : given;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskTemplatePort.Resemblance> resembling(String title, int limit) {
        return templates.resembling(title, Math.min(Math.max(limit, 1), 10));
    }

    @Override
    public List<ProcessShapeHint> inspectShape(TemplateDetails details) {
        return shapeHeuristic.inspect(details);
    }

    private TaskTemplate persist(TaskTemplate template) {
        templates.save(template);
        return template;
    }

    private TaskTemplate load(UUID id) {
        return templates.byId(id).orElseThrow(() -> new TemplateNotFoundException("no such template"));
    }

    private UUID requireCaller() {
        return caller.currentCaller()
                .orElseThrow(() -> new IllegalStateException("there is no session behind this call"));
    }
}
