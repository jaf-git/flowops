package com.flowops.workspace.application.manageorganisation;

public class DepartmentNameTakenException extends RuntimeException {
    public DepartmentNameTakenException(String name) {
        super("a department already goes by " + name);
    }
}
