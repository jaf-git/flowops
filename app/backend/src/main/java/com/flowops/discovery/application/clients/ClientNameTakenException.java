package com.flowops.discovery.application.clients;

public class ClientNameTakenException extends RuntimeException {
    public ClientNameTakenException(String name) {
        super("a client already goes by " + name);
    }
}
