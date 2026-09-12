package com.flowops.task.api.dto;

import java.util.List;
import java.util.UUID;

public record AssignablePeopleResponse(List<AssignablePerson> people) {
    public record AssignablePerson(UUID id, String displayName) {}
}
