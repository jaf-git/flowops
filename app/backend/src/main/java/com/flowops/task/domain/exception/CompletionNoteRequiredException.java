package com.flowops.task.domain.exception;

public class CompletionNoteRequiredException extends RuntimeException {
    public CompletionNoteRequiredException() {
        super("completing is showing what was done, not asserting that it was");
    }
}
