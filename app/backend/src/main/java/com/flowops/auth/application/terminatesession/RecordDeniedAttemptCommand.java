package com.flowops.auth.application.terminatesession;

import com.flowops.auth.application.shared.ClientContext;
import com.flowops.auth.domain.enums.AuthAction;
import com.flowops.auth.domain.model.UserId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record RecordDeniedAttemptCommand(
        AuthAction action, UUID sessionReference, UserId subject, ClientContext clientContext) {
    public RecordDeniedAttemptCommand {
        Objects.requireNonNull(action, "an action is required");
        Objects.requireNonNull(clientContext, "a client context is required");
    }

    public static RecordDeniedAttemptCommand terminationRefused(UUID sessionReference, ClientContext clientContext) {
        return new RecordDeniedAttemptCommand(
                AuthAction.SESSION_TERMINATION_DENIED, sessionReference, null, clientContext);
    }

    public static RecordDeniedAttemptCommand viewRefused(UserId subject, ClientContext clientContext) {
        return new RecordDeniedAttemptCommand(AuthAction.SESSION_VIEW_DENIED, null, subject, clientContext);
    }

    public Optional<UUID> attemptedSessionReference() {
        return Optional.ofNullable(sessionReference);
    }

    public Optional<UserId> namedSubject() {
        return Optional.ofNullable(subject);
    }
}
