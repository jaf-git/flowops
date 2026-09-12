package com.flowops.canvas.application.stream;

public class StreamNotAvailableException extends RuntimeException {
    public StreamNotAvailableException() {
        super("no stream is available for this caller");
    }
}
