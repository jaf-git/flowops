package com.flowops.shared.event;

import java.util.UUID;

public record FunctionalRoleRenamed(UUID roleId, String name) {}
