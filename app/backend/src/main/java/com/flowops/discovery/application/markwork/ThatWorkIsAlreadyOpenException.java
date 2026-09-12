package com.flowops.discovery.application.markwork;

import java.util.UUID;

public class ThatWorkIsAlreadyOpenException extends RuntimeException {
    private final UUID alreadyOpen;
    private final String destination;

    public ThatWorkIsAlreadyOpenException(UUID alreadyOpen, String destination) {
        super("R1.1 - work is already open at " + destination);
        this.alreadyOpen = alreadyOpen;
        this.destination = destination;
    }

    public UUID alreadyOpen() {
        return alreadyOpen;
    }

    public String destination() {
        return destination;
    }
}
