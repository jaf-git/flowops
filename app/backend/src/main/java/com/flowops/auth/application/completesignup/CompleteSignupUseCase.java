package com.flowops.auth.application.completesignup;

import com.flowops.auth.application.shared.SessionContext;

public interface CompleteSignupUseCase {
    SessionContext execute(CompleteSignupCommand command);
}
