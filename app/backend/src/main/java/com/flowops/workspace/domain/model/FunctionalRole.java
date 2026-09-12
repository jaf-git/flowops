package com.flowops.workspace.domain.model;

import java.util.UUID;

public record FunctionalRole(UUID id, String name, UUID departmentId) {}
