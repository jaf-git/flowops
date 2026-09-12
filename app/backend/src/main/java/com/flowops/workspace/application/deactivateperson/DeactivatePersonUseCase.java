package com.flowops.workspace.application.deactivateperson;

public interface DeactivatePersonUseCase {
    DeactivatePersonResult execute(DeactivatePersonCommand command);
}
