package com.flowops.chat.application.shared.port;

import java.util.UUID;

public interface AssignableCheckPort {
    boolean mayAssign(UUID person);
}
