package com.flowops.tasklib.application.exception;

public class UnknownBandException extends RuntimeException {
    private final String band;

    public UnknownBandException(String band) {
        super("no such band: " + band);
        this.band = band;
    }

    public String band() {
        return band;
    }
}
