package com.flowops.task.domain.exception;

public class CommentBodyRequiredException extends RuntimeException {
    public CommentBodyRequiredException() {
        super("a comment says something");
    }
}
