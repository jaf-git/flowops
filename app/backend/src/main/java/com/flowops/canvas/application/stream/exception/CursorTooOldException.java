package com.flowops.canvas.application.stream.exception;

public class CursorTooOldException extends RuntimeException {
    public CursorTooOldException(long cursor, long live) {
        super("the cursor " + cursor + " is further behind " + live + " than this stream replays");
    }
}
