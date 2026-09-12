package com.flowops.shared.domain;

public abstract class RefusedByDomain extends RuntimeException {
    private final String code;

    protected RefusedByDomain(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
