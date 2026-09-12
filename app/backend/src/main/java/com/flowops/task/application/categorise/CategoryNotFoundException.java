package com.flowops.task.application.categorise;

public class CategoryNotFoundException extends RuntimeException {
    public CategoryNotFoundException() {
        super("There is no such group here.");
    }
}
