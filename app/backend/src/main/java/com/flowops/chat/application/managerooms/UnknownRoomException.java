package com.flowops.chat.application.managerooms;

import java.util.UUID;

public class UnknownRoomException extends RuntimeException {
    public UnknownRoomException(UUID id) {
        super("no conversation " + id);
    }
}
