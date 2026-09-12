package com.flowops.process.application.categorise;

public class CategoryNotFoundException extends RuntimeException {
    public CategoryNotFoundException() {
        super("There is no such group.");
    }
}
