package com.flowops.nodepipeline.application;

import java.time.Instant;

/**
 * Raised when a caller asks the pipeline to read a window that ends before it begins, or that
 * begins and ends at the same instant.
 *
 * <p>The rule used to live only in {@code analysis_run}'s {@code window_runs_forwards} check
 * constraint, which meant the first statement of the run's transaction failed and the caller was
 * told {@code INTERNAL_ERROR}. A window is the caller's input, so the use case refuses it before it
 * opens a run, and the database constraint goes back to being the backstop it was meant to be.
 */
public class WindowRunsBackwardsException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final transient Instant from;
    private final transient Instant to;

    public WindowRunsBackwardsException(Instant from, Instant to) {
        super("a window runs forwards: " + from + " is not before " + to);
        this.from = from;
        this.to = to;
    }

    public Instant from() {
        return from;
    }

    public Instant to() {
        return to;
    }
}
