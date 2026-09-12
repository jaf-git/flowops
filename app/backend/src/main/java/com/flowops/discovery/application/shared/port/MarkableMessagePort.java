package com.flowops.discovery.application.shared.port;

import java.util.Optional;
import java.util.UUID;

public interface MarkableMessagePort {
    Optional<Words> read(UUID messageId, UUID callerId);

    boolean alreadyMarked(UUID messageId);

    boolean participatesIn(UUID conversationId, UUID callerId);

    record Words(String body, UUID authorId, UUID conversationId) {}
}
