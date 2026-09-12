package com.flowops.workspace.domain.exception;

public class QuietHoursCoverTheDayException extends RuntimeException {
    public QuietHoursCoverTheDayException() {
        super("quiet hours cannot cover the whole day");
    }
}
