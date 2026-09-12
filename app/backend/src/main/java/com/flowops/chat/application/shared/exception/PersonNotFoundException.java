package com.flowops.chat.application.shared.exception;

public class PersonNotFoundException extends RuntimeException {
    public PersonNotFoundException() {
        super("no such person in this workspace");
    }
}
