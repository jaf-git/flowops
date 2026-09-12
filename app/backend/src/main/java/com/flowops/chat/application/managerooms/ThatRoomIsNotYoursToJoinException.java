package com.flowops.chat.application.managerooms;

public class ThatRoomIsNotYoursToJoinException extends RuntimeException {
    private final String kind;

    public ThatRoomIsNotYoursToJoinException(String kind) {
        super("a " + kind + " does not take members by choice");
        this.kind = kind;
    }

    public String kind() {
        return kind;
    }
}
