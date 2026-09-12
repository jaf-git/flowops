package com.flowops.canvas.application.stream;

import java.util.UUID;

public record CanvasDelta(long cursor, UUID task, String kind) {}
