package com.flowops.shared.event;

import java.time.Instant;
import java.util.UUID;

public record TaskStateChanged(UUID task, String state, boolean assigned, Instant at) {}
