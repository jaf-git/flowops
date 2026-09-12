package com.flowops.process.domain.model;

import java.util.Objects;
import java.util.UUID;

public record TemplateId(UUID value) {
    public TemplateId {
        Objects.requireNonNull(value, "a template identifier is required");
    }

    public static TemplateId of(UUID value) {
        return new TemplateId(value);
    }
}
