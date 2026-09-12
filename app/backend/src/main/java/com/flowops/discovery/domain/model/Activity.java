package com.flowops.discovery.domain.model;

import com.flowops.discovery.domain.enums.ActivityStatus;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class Activity {
    private static final int LONGEST_NAME = 120;

    private final UUID id;
    private final UUID createdBy;
    private final Instant createdAt;

    private String name;
    private String slug;
    private ActivityStatus status;
    private UUID mergedIntoId;
    private int timesUsed;
    private Instant lastUsedAt;

    private Activity(
            UUID id,
            String name,
            String slug,
            ActivityStatus status,
            UUID mergedIntoId,
            int timesUsed,
            Instant lastUsedAt,
            UUID createdBy,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "an activity needs an identity");
        this.name = requireName(name);
        this.slug = Objects.requireNonNull(slug);
        this.status = Objects.requireNonNull(status);
        this.mergedIntoId = mergedIntoId;
        this.timesUsed = timesUsed;
        this.lastUsedAt = lastUsedAt;
        this.createdBy = Objects.requireNonNull(createdBy, "somebody named this activity, and who is part of it");
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static Activity named(UUID id, String name, UUID by, Instant at) {
        String cleaned = requireName(name);
        return new Activity(id, cleaned, slugOf(cleaned), ActivityStatus.ACTIVE, null, 0, null, by, at);
    }

    public static Activity rehydrated(
            UUID id,
            String name,
            String slug,
            ActivityStatus status,
            UUID mergedIntoId,
            int timesUsed,
            Instant lastUsedAt,
            UUID createdBy,
            Instant createdAt) {
        return new Activity(id, name, slug, status, mergedIntoId, timesUsed, lastUsedAt, createdBy, createdAt);
    }

    public void renamedTo(String newName) {
        String cleaned = requireName(newName);
        String reslugged = slugOf(cleaned);
        if (!reslugged.equals(slug)) {
            throw new IllegalArgumentException("renaming \"" + name + "\" to \"" + cleaned
                    + "\" changes its identity, and every step already keyed on it would silently become a "
                    + "different step; merge the two activities instead");
        }
        this.name = cleaned;
    }

    public void usedAt(Instant at) {
        if (!status.isChoosable()) {
            throw new IllegalStateException("activity " + id + " is " + status + " and nobody can still pick it");
        }
        this.timesUsed = timesUsed + 1;
        this.lastUsedAt = Objects.requireNonNull(at);
    }

    public void mergedInto(UUID survivor) {
        Objects.requireNonNull(survivor, "a merge names the activity that survives it");
        if (survivor.equals(id)) {
            throw new IllegalArgumentException("activity " + id + " cannot be merged into itself");
        }
        this.status = ActivityStatus.MERGED;
        this.mergedIntoId = survivor;
    }

    public void absorbed(Activity other) {
        Objects.requireNonNull(other, "a merge has two sides");
        this.timesUsed = timesUsed + other.timesUsed();
        this.lastUsedAt = other.lastUsedAt()
                .filter(theirs -> lastUsedAt == null || theirs.isAfter(lastUsedAt))
                .orElse(lastUsedAt);
    }

    public void retired() {
        this.status = ActivityStatus.RETIRED;
        this.mergedIntoId = null;
    }

    public static String slugOf(String name) {
        String folded = Normalizer.normalize(requireName(name), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");

        if (folded.isEmpty()) {
            throw new IllegalArgumentException(
                    "\"" + name + "\" leaves nothing behind once it is normalised, so no two people could ever "
                            + "arrive at it from a keyboard; name the activity in words");
        }
        return folded.length() > LONGEST_NAME
                ? folded.substring(0, LONGEST_NAME).replaceAll("-+$", "")
                : folded;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("an activity needs a name somebody would recognise their work by");
        }
        String trimmed = name.trim();
        if (trimmed.length() > LONGEST_NAME) {
            throw new IllegalArgumentException("an activity name is a few words, not " + trimmed.length()
                    + " characters; what is long enough to need that is a description");
        }
        return trimmed;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String slug() {
        return slug;
    }

    public ActivityStatus status() {
        return status;
    }

    public Optional<UUID> mergedIntoId() {
        return Optional.ofNullable(mergedIntoId);
    }

    public int timesUsed() {
        return timesUsed;
    }

    public Optional<Instant> lastUsedAt() {
        return Optional.ofNullable(lastUsedAt);
    }

    public UUID createdBy() {
        return createdBy;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
