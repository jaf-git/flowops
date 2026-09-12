package com.flowops.analyser.domain;

import java.util.Objects;

public record Precondition(String needed, String had, boolean met, String remedy) {
    public Precondition {
        Objects.requireNonNull(needed, "a precondition that does not say what it needed explains nothing");
        Objects.requireNonNull(had, "and one that does not say what was there explains half");
        if (!met) {
            Objects.requireNonNull(remedy, "an unmet precondition that does not say what would fix it is a complaint");
        }
    }

    public static Precondition met(String needed, String had) {
        return new Precondition(needed, had, true, null);
    }

    public static Precondition unmet(String needed, String had, String remedy) {
        return new Precondition(needed, had, false, remedy);
    }
}
