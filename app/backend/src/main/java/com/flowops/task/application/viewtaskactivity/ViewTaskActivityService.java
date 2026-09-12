package com.flowops.task.application.viewtaskactivity;

import com.flowops.task.application.shared.TaskReviewSupport;
import com.flowops.task.application.shared.exception.NotAuthenticatedException;
import com.flowops.task.application.shared.exception.TaskNotFoundException;
import com.flowops.task.application.shared.exception.TaskOutOfScopeException;
import com.flowops.task.application.shared.port.IdentifyCallerPort;
import com.flowops.task.application.shared.port.LoadPersonPort;
import com.flowops.task.application.shared.port.LoadTaskPort;
import com.flowops.task.application.shared.port.LoadTransitionPort;
import com.flowops.task.application.shared.port.TaskCommentPort;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.Task;
import com.flowops.task.domain.model.TaskId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViewTaskActivityService implements ViewTaskActivityUseCase {
    private final IdentifyCallerPort identifyCallerPort;
    private final LoadTaskPort loadTaskPort;
    private final TaskReviewSupport visibility;
    private final LoadTransitionPort transitions;
    private final TaskCommentPort comments;
    private final LoadPersonPort loadPersonPort;

    public ViewTaskActivityService(
            IdentifyCallerPort identifyCallerPort,
            LoadTaskPort loadTaskPort,
            TaskReviewSupport visibility,
            LoadTransitionPort transitions,
            TaskCommentPort comments,
            LoadPersonPort loadPersonPort) {
        this.identifyCallerPort = identifyCallerPort;
        this.loadTaskPort = loadTaskPort;
        this.visibility = visibility;
        this.transitions = transitions;
        this.comments = comments;
        this.loadPersonPort = loadPersonPort;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivityEntry> execute(TaskId task) {
        PersonId caller = identifyCallerPort
                .currentCaller()
                .orElseThrow(() -> new NotAuthenticatedException("there is no session behind this call"));

        Task found = loadTaskPort.findById(task).orElseThrow(() -> new TaskNotFoundException("there is no such task"));
        if (!visibility.reaches(caller, found)) {
            throw new TaskOutOfScopeException("this work belongs to somebody outside your team");
        }

        Map<PersonId, String> names = new HashMap<>();

        List<ActivityEntry> activity = new ArrayList<>();
        transitions
                .allOf(task)
                .forEach(move -> activity.add(new ActivityEntry(
                        ActivityEntry.TRANSITION,
                        move.occurredAt(),
                        move.actor(),
                        nameOf(move.actor(), names),
                        move.cameFrom().orElse(null),
                        move.to(),
                        move.statedReason().orElse(null),
                        move.overridden(),
                        null)));
        comments.allOf(task)
                .forEach(comment -> activity.add(new ActivityEntry(
                        ActivityEntry.COMMENT,
                        comment.writtenAt(),
                        comment.author(),
                        nameOf(comment.author(), names),
                        null,
                        null,
                        null,
                        false,
                        comment.body())));

        activity.sort(Comparator.comparing(ActivityEntry::occurredAt));
        return List.copyOf(activity);
    }

    private String nameOf(PersonId person, Map<PersonId, String> cache) {
        return cache.computeIfAbsent(person, id -> loadPersonPort
                .describe(id)
                .map(LoadPersonPort.Person::displayName)
                .orElse(""));
    }
}
