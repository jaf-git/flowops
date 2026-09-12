package com.flowops.task.application.shared.port;

import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.Task;
import com.flowops.task.domain.model.TaskId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LoadTaskPort {
    Optional<Task> lockForTransition(TaskId id);

    Optional<Task> findById(TaskId id);

    List<Task> findByAssignees(Collection<PersonId> assignees);

    List<Task> findByAssigneesOrCreator(Collection<PersonId> assignees, PersonId creator);

    List<Task> findAll();

    List<Task> findCompletedByAssignees(Collection<PersonId> assignees);

    List<Task> findAllCompleted();
}
