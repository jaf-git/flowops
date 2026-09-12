package com.flowops.discovery.application.declarewait;

import java.util.UUID;

public class ItsTargetDecidesWhenItArrivedException extends RuntimeException {
    public ItsTargetDecidesWhenItArrivedException(UUID id) {
        super("wait " + id + " names work inside this graph; that bracket's own close is what satisfies it");
    }
}
