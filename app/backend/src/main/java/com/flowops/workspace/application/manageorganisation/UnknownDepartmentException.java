package com.flowops.workspace.application.manageorganisation;

import java.util.UUID;

public class UnknownDepartmentException extends RuntimeException {
    public UnknownDepartmentException(UUID id) {
        super("no department " + id);
    }
}
