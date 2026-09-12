package com.flowops.process.application.startfromtasks;

import com.flowops.process.domain.model.PersonId;
import java.time.Instant;
import java.util.List;

public record StartFromDescriptionsCommand(String name, PersonId processOwner, List<NewStep> steps) {
    public record NewStep(String title, String description, PersonId assignee, Instant deadline, String priority) {}
}
