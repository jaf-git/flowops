package com.flowops.auth.application.shared.port;

import com.flowops.auth.domain.model.UserId;
import java.util.Set;

public interface ResolvePermissionsPort {
    Set<String> resolveFor(UserId userId);
}
