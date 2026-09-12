package com.flowops.canvas.application.stream;

import java.util.UUID;

public record CanvasSubscription(UUID id, UUID person, UUID instance, CanvasSink sink) {}
