package com.flowops.chat.domain.model;

import java.util.Objects;
import java.util.UUID;

public record MessageId(UUID value) {
    public MessageId {
        Objects.requireNonNull(value, "a message identifier is required");
    }

    public static MessageId of(UUID value) {
        return new MessageId(value);
    }
}
