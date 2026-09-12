package com.flowops.process.application.categorise;

public class CategoryNameTakenException extends RuntimeException {
    public CategoryNameTakenException(String name) {
        super("There is already a group called %s.".formatted(name));
    }
}
