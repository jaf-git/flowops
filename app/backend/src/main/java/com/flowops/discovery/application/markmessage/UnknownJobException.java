package com.flowops.discovery.application.markmessage;

import java.util.UUID;

public class UnknownJobException extends RuntimeException {
    public UnknownJobException(UUID jobId) {
        super("there is no engagement with the identifier " + jobId);
    }
}
