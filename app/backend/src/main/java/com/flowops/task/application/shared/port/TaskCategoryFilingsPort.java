package com.flowops.task.application.shared.port;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface TaskCategoryFilingsPort {
    record Filing(UUID categoryId, String categoryName) {}

    Map<UUID, Filing> filingsOf(Collection<UUID> taskIds);
}
