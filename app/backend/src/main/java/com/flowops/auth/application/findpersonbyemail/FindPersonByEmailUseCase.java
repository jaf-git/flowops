package com.flowops.auth.application.findpersonbyemail;

import java.util.Optional;
import java.util.UUID;

public interface FindPersonByEmailUseCase {
    Optional<UUID> execute(String email);
}
