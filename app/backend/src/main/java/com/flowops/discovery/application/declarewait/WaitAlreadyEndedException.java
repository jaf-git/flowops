package com.flowops.discovery.application.declarewait;

import java.util.UUID;

public class WaitAlreadyEndedException extends RuntimeException {
    public WaitAlreadyEndedException(UUID id) {
        super("wait " + id + " has already been satisfied or withdrawn");
    }
}
