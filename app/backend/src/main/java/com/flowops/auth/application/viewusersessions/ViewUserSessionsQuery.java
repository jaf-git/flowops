package com.flowops.auth.application.viewusersessions;

import com.flowops.auth.application.shared.ClientContext;
import com.flowops.auth.domain.model.UserId;
import java.util.Objects;

public record ViewUserSessionsQuery(UserId subject, ClientContext clientContext) {
    public ViewUserSessionsQuery {
        Objects.requireNonNull(subject, "a subject is required");
        Objects.requireNonNull(clientContext, "a client context is required");
    }
}
