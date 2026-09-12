package com.flowops.chat.infrastructure.process;

import com.flowops.chat.application.shared.port.AddTaskToInstancePort;
import com.flowops.chat.application.shared.port.AttachableInstancesPort;
import com.flowops.chat.application.shared.port.BuildProcessPort;
import com.flowops.chat.application.shared.port.ChatCallerPort;
import com.flowops.chat.application.shared.port.StartRunPort;
import com.flowops.process.application.addtask.AddTaskToInstanceCommand;
import com.flowops.process.application.addtask.AddTaskToInstanceUseCase;
import com.flowops.process.application.addtask.ViewAttachableInstancesUseCase;
import com.flowops.process.application.published.ProcessAuthoringUseCase;
import com.flowops.process.application.published.ProcessInstantiationUseCase;
import com.flowops.tasklib.application.published.TemplateResolutionUseCase;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class ChatProcessAdapter
        implements AttachableInstancesPort, AddTaskToInstancePort, StartRunPort, BuildProcessPort {
    private final ViewAttachableInstancesUseCase attachableInstances;
    private final AddTaskToInstanceUseCase addTaskToInstance;
    private final ProcessInstantiationUseCase instantiation;
    private final ProcessAuthoringUseCase authoring;
    private final TemplateResolutionUseCase templates;
    private final ChatCallerPort caller;

    public ChatProcessAdapter(
            ViewAttachableInstancesUseCase attachableInstances,
            AddTaskToInstanceUseCase addTaskToInstance,
            ProcessInstantiationUseCase instantiation,
            ProcessAuthoringUseCase authoring,
            TemplateResolutionUseCase templates,
            ChatCallerPort caller) {
        this.attachableInstances = attachableInstances;
        this.addTaskToInstance = addTaskToInstance;
        this.instantiation = instantiation;
        this.authoring = authoring;
        this.templates = templates;
        this.caller = caller;
    }

    @Override
    public StartedRun startRunFromDescriptions(String name, UUID processOwnerId, List<NewStep> steps) {
        ProcessInstantiationUseCase.StartedRun started = instantiation.startRunFromDescriptions(
                name,
                processOwnerId,
                steps.stream()
                        .map(step -> new ProcessInstantiationUseCase.NewStep(
                                step.title(), step.description(), step.assignee(), step.deadline(), step.priority()))
                        .toList());
        return new StartedRun(started.instanceId(), started.taskIds());
    }

    @Override
    public void appendStepsToTemplate(UUID templateId, List<TemplateStep> steps) {
        UUID author = caller.currentCaller()
                .orElseThrow(() -> new AccessDeniedException("building a process requires a signed-in caller"));
        authoring.appendSteps(
                templateId,
                steps.stream()
                        .map(step -> new ProcessAuthoringUseCase.StepSpecification(
                                templates.resolve(step.title(), step.description(), author), null))
                        .toList());
    }

    @Override
    public StartableTemplates startableTemplates() {
        try {
            return new StartableTemplates(
                    true,
                    instantiation.startableTemplates().stream()
                            .map(template -> new StartableTemplate(
                                    template.id(), template.name(), template.overview(), template.stepCount()))
                            .toList());
        } catch (AccessDeniedException mayNot) {
            return new StartableTemplates(false, List.of());
        }
    }

    @Override
    public UUID startRun(UUID templateId, UUID processOwner) {
        return instantiation.startRun(templateId, null, processOwner);
    }

    @Override
    public List<Attachable> forSomebodyToChoose(Optional<UUID> preferring) {
        return attachableInstances.forSomebodyToChoose(preferring).stream()
                .map(run -> new Attachable(run.id(), run.name()))
                .toList();
    }

    @Override
    public UUID createAndAttach(
            UUID instance, String title, String description, UUID assignee, Instant deadline, String priority) {
        return addTaskToInstance
                .execute(new AddTaskToInstanceCommand(
                        instance,
                        null,
                        new AddTaskToInstanceCommand.NewTask(title, description, assignee, deadline, priority),
                        List.of()))
                .taskId();
    }
}
