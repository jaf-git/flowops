package com.flowops.process.application.startfromtasks;

import com.flowops.process.domain.model.PersonId;
import com.flowops.process.domain.model.TaskRef;
import java.util.List;

public record StartFromTasksCommand(String name, PersonId processOwner, List<TaskRef> tasks) {}
