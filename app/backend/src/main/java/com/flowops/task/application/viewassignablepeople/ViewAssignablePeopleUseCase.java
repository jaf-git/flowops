package com.flowops.task.application.viewassignablepeople;

import java.util.List;

public interface ViewAssignablePeopleUseCase {
    List<AssignablePerson> execute();

    List<AssignablePerson> executeFor(java.util.UUID creator);

    record AssignablePerson(java.util.UUID id, String displayName) {}
}
