package com.flowops.shared.event;

import java.time.Instant;
import java.util.UUID;

public record FreeFormTaskCreated(UUID task, Instant at) {}
