package com.flowops.shared.event;

import java.util.UUID;

public record FunctionalRoleCreated(UUID roleId, String name) {}
