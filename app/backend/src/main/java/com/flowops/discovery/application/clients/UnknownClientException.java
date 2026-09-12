package com.flowops.discovery.application.clients;

import java.util.UUID;

public class UnknownClientException extends RuntimeException {
    public UnknownClientException(UUID id) {
        super("no counterparty " + id);
    }
}
