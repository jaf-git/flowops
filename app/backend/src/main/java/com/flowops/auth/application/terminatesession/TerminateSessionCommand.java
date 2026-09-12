package com.flowops.auth.application.terminatesession;

import com.flowops.auth.application.shared.ClientContext;
import java.util.Objects;
import java.util.UUID;

public record TerminateSessionCommand(UUID reference, ClientContext clientContext) {
    public TerminateSessionCommand {
        Objects.requireNonNull(reference, "a session reference is required");
        Objects.requireNonNull(clientContext, "a client context is required");
    }
}
