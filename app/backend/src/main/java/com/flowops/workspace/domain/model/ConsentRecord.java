package com.flowops.workspace.domain.model;

import java.time.Instant;
import java.util.Objects;

public record ConsentRecord(
        ConsentRecordId id, PersonId person, String language, String version, String text, Instant agreedAt) {
    public ConsentRecord {
        Objects.requireNonNull(id, "a consent record has an identity");
        Objects.requireNonNull(person, "a consent record names the person who agreed");
        Objects.requireNonNull(language, "a consent record names the language the person read");
        Objects.requireNonNull(version, "a consent record names the version agreed to");
        Objects.requireNonNull(text, "a consent record holds the whole text, not a pointer to it");
        Objects.requireNonNull(agreedAt, "a consent record records when consent was given");
        if (text.isBlank()) {
            throw new IllegalArgumentException("a consent record cannot hold empty text");
        }
    }
}
