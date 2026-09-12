package com.flowops.discovery.application.clients;

import java.util.UUID;

public class ClientHasEngagementsException extends RuntimeException {
    private final long engagements;

    public ClientHasEngagementsException(UUID id, long engagements) {
        super("counterparty " + id + " is named by " + engagements + " engagements");
        this.engagements = engagements;
    }

    public long engagements() {
        return engagements;
    }
}
