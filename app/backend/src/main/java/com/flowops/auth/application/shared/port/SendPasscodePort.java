package com.flowops.auth.application.shared.port;

import com.flowops.auth.domain.model.EmailAddress;

public interface SendPasscodePort {
    void sendPasscode(EmailAddress email, String rawCode);

    void sendDuplicateSignupNotice(EmailAddress email);
}
