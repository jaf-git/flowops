package com.flowops.auth.application.shared.port;

import com.flowops.auth.domain.model.EmailAddress;

public interface SendResetLinkPort {
    void sendResetLink(EmailAddress email, String rawToken);
}
