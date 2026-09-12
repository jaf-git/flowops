package com.flowops.auth.application.shared.port;

import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.auth.domain.model.SignupPasscode;
import java.util.Optional;

public interface LoadPasscodePort {
    Optional<SignupPasscode> loadLatestFor(EmailAddress email);
}
