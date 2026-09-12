package com.flowops.nodepipeline.domain.notify;

public record NoticeBudget(int perPersonPerDigest, int habitRepeats, int cadenceDays) {
    public NoticeBudget {
        if (perPersonPerDigest < 1 || habitRepeats < 1 || cadenceDays < 1) {
            throw new IllegalArgumentException("a budget of nothing is a feature that is off, not a budget");
        }
    }

    public static NoticeBudget reference() {
        return new NoticeBudget(3, 2, 7);
    }
}
