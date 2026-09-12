package com.flowops.chatassist.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProposedWork(
        WorkShape shape, Sourced<String> title, Sourced<UUID> assigneeId, Sourced<Instant> deadline, List<Step> steps) {
    public ProposedWork {
        steps = List.copyOf(steps);
    }

    public record Sourced<T>(T value, FieldSource source) {
        public static <T> Sourced<T> quoted(T value) {
            return new Sourced<>(value, FieldSource.FROM_CONVERSATION);
        }

        public static <T> Sourced<T> fromHistory(T value) {
            return new Sourced<>(value, FieldSource.FROM_HISTORY);
        }

        public static <T> Sourced<T> suggested(T value) {
            return new Sourced<>(value, FieldSource.SUGGESTED);
        }
    }

    public record Step(Sourced<String> title, String quotedFrom, Sourced<UUID> assigneeId, Sourced<Instant> deadline) {}

    public boolean isProcess() {
        return shape == WorkShape.PROCESS;
    }

    public boolean isEmpty() {
        return steps.isEmpty();
    }
}
