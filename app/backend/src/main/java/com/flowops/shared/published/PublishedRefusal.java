package com.flowops.shared.published;

public class PublishedRefusal extends RuntimeException {
    private final String code;
    private final RefusalKind kind;

    public PublishedRefusal(RefusalKind kind, String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.kind = kind;
    }

    public String code() {
        return code;
    }

    public RefusalKind kind() {
        return kind;
    }
}
