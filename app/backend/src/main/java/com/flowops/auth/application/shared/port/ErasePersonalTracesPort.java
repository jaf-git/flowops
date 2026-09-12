package com.flowops.auth.application.shared.port;

import com.flowops.auth.domain.model.EmailAddress;

public interface ErasePersonalTracesPort {
    void eraseTracesOf(EmailAddress address);
}
