package com.flowops.discovery.application.declarewait;

import java.util.UUID;

public class UnknownWaitException extends RuntimeException {
    public UnknownWaitException(UUID id) {
        super("no wait " + id);
    }
}
