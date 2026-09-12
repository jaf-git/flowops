package com.flowops.analyser.domain;

import java.util.Objects;

public record Clean(String what, String detail) {
    public Clean {
        Objects.requireNonNull(what, "a clean result with no name cannot be grouped or counted");
        Objects.requireNonNull(
                detail, "a clean result with no sentence says 'nothing to report', which is what it exists to replace");
    }
}
