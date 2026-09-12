package com.flowops.auth.application.anonymiseperson;

import java.util.UUID;

public interface AnonymisePersonUseCase {
    void execute(UUID personId);
}
