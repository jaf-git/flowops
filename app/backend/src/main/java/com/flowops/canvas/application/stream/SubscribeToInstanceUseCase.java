package com.flowops.canvas.application.stream;

import java.util.Set;
import java.util.UUID;

public interface SubscribeToInstanceUseCase {
    UUID subscribe(UUID person, Set<String> permissions, UUID instance, Long cursor, CanvasSink sink);

    void unsubscribe(UUID subscription);

    long currentCursor();
}
