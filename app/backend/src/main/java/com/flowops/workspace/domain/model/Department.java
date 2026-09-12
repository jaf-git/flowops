package com.flowops.workspace.domain.model;

import java.util.List;
import java.util.UUID;

public record Department(UUID id, String name, List<FunctionalRole> roles) {
    public Department {
        roles = List.copyOf(roles);
    }
}
