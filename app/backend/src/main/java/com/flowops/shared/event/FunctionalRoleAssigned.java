package com.flowops.shared.event;

import java.util.UUID;

public record FunctionalRoleAssigned(UUID personId, UUID roleId, UUID previousRoleId) {}
