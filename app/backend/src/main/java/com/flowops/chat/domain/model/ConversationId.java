package com.flowops.chat.domain.model;

import java.util.Objects;
import java.util.UUID;

public record ConversationId(UUID value) {
    public ConversationId {
        Objects.requireNonNull(value, "a conversation identifier is required");
    }

    public static ConversationId of(UUID value) {
        return new ConversationId(value);
    }
}
