package com.flowops.chat.application.shared.port;

import java.time.Instant;
import java.util.UUID;

public interface CreateTaskPort {
    UUID createAdHoc(String title, String description, UUID assignee, Instant deadline, String priority, UUID template);
}
