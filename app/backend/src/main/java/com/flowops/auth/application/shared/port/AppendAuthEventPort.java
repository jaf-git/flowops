package com.flowops.auth.application.shared.port;

import com.flowops.auth.domain.event.AuthEvent;

public interface AppendAuthEventPort {
    void append(AuthEvent event);
}
