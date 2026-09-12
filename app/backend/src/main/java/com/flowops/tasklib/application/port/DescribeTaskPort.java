package com.flowops.tasklib.application.port;

import java.util.Optional;
import java.util.UUID;

public interface DescribeTaskPort {
    Optional<Words> describe(UUID task);

    record Words(String title, String description, String priority) {}
}
