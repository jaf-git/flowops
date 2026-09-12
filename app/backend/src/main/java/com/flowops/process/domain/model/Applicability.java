package com.flowops.process.domain.model;

import com.flowops.process.domain.exception.ConditionNeedsAnOptionalStepException;

public record Applicability(boolean optional, String conditionNote) {
    public Applicability {
        conditionNote = conditionNote == null || conditionNote.isBlank() ? null : conditionNote.trim();
        if (conditionNote != null && !optional) {
            throw new ConditionNeedsAnOptionalStepException();
        }
    }

    public static Applicability always() {
        return new Applicability(false, null);
    }

    public static Applicability when(String conditionNote) {
        return new Applicability(true, conditionNote);
    }

    public boolean needsADecision() {
        return optional;
    }
}
