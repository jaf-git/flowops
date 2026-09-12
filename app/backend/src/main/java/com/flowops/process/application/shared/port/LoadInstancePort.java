package com.flowops.process.application.shared.port;

import com.flowops.process.domain.model.InstanceId;
import com.flowops.process.domain.model.PersonId;
import com.flowops.process.domain.model.ProcessInstance;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface LoadInstancePort {
    Optional<ProcessInstance> findById(InstanceId id);

    List<ProcessInstance> findInvolving(Set<PersonId> people);

    List<ProcessInstance> findAll();

    List<ProcessInstance> findOnTheBoard();

    List<ProcessInstance> findOnTheBoardInvolving(Set<PersonId> people);

    Optional<ProcessInstance> findByTask(com.flowops.process.domain.model.TaskRef task);

    List<ProcessInstance> findRunning();
}
