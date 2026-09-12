package com.flowops.analyser.domain;

import java.util.Objects;

public record Absence(String what, String detail, boolean blocking) {
    public Absence {
        Objects.requireNonNull(what, "an absence with no name cannot be grouped or counted");
        Objects.requireNonNull(detail, "an absence with no sentence is a code nobody can act on");
    }
}
