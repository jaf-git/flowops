package com.flowops.discovery.application.declarewait;

import java.util.UUID;

public class WaitIsNotYoursToDeclareException extends RuntimeException {
    public WaitIsNotYoursToDeclareException(UUID bracketId) {
        super("bracket " + bracketId + " is not held by the caller, so they may not declare it blocked");
    }
}
