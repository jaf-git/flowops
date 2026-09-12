package com.flowops.discovery.domain.model;

import com.flowops.discovery.domain.enums.CounterpartyKind;
import com.flowops.discovery.domain.enums.ReclassificationReason;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class Counterparty {
    private final UUID id;

    private String name;
    private CounterpartyKind kind;
    private UUID classifiedBy;
    private Instant classifiedAt;

    private Counterparty(UUID id, String name, CounterpartyKind kind, UUID classifiedBy, Instant classifiedAt) {
        this.id = Objects.requireNonNull(id, "a counterparty needs an identity");
        this.name = requireName(name);
        this.kind = Objects.requireNonNull(kind);
        this.classifiedBy = classifiedBy;
        this.classifiedAt = classifiedAt;
    }

    public static Counterparty named(UUID id, String name) {
        return new Counterparty(id, name, CounterpartyKind.UNCLASSIFIED, null, null);
    }

    public static Counterparty rehydrated(
            UUID id, String name, CounterpartyKind kind, UUID classifiedBy, Instant classifiedAt) {
        return new Counterparty(id, name, kind, classifiedBy, classifiedAt);
    }

    public void classifiedAs(CounterpartyKind newKind, UUID by, Instant at) {
        Objects.requireNonNull(newKind, "a classification names a kind");
        if (newKind == CounterpartyKind.UNCLASSIFIED) {
            throw new IllegalArgumentException("classifying something as unclassified says nothing; leave it as it is");
        }
        if (kind.isClassified()) {
            throw new IllegalStateException("counterparty " + id + " is already " + kind
                    + "; a change from here is a conversion or a correction, and the difference is recorded");
        }
        this.kind = newKind;
        this.classifiedBy = Objects.requireNonNull(by);
        this.classifiedAt = Objects.requireNonNull(at);
    }

    public void reclassifiedTo(CounterpartyKind newKind, UUID by, ReclassificationReason reason) {
        Objects.requireNonNull(newKind, "a reclassification names a kind");
        Objects.requireNonNull(by, "somebody made this decision, and who it was is part of it");
        Objects.requireNonNull(
                reason,
                "a kind change without a reason cannot be dated, and an undated conversion erases what winning the "
                        + "client cost");
        if (newKind == kind) {
            throw new IllegalArgumentException("counterparty " + id + " is already " + kind);
        }
        this.kind = newKind;
        this.classifiedBy = by;
    }

    public void renamedTo(String newName) {
        this.name = requireName(newName);
    }

    public boolean isClassified() {
        return kind.isClassified();
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("a counterparty needs a name somebody can recognise it by");
        }
        return name.trim();
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public CounterpartyKind kind() {
        return kind;
    }

    public Optional<UUID> classifiedBy() {
        return Optional.ofNullable(classifiedBy);
    }

    public Optional<Instant> classifiedAt() {
        return Optional.ofNullable(classifiedAt);
    }
}
