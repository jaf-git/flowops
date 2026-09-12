package com.flowops.discovery.application.declarewait;

import java.util.UUID;

public class WaitIsNotYoursToEndException extends RuntimeException {
    public WaitIsNotYoursToEndException(UUID id) {
        super("wait " + id + " blocks work the caller does not hold");
    }
}
