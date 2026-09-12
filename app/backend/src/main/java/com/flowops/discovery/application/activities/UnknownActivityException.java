package com.flowops.discovery.application.activities;

import java.util.UUID;

public class UnknownActivityException extends RuntimeException {
    public UnknownActivityException(UUID id) {
        super("no activity " + id);
    }
}
