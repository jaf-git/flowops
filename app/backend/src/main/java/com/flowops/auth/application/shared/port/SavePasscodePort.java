package com.flowops.auth.application.shared.port;

import com.flowops.auth.domain.model.SignupPasscode;

public interface SavePasscodePort {
    void save(SignupPasscode passcode);
}
