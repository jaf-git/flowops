package com.flowops.process.domain.exception;

public class ClosureNoteRequiredException extends RuntimeException {
    public ClosureNoteRequiredException() {
        super("say why the run is finished while work is still open on it");
    }
}
