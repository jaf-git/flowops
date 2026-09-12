package com.flowops.task.application.categorise;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface TaskCategoryUseCase {
    List<Category> all();

    Map<UUID, UUID> filings();

    UUID create(String name);

    void rename(UUID categoryId, String name);

    void delete(UUID categoryId);

    void fileTask(UUID taskId, Optional<UUID> categoryId);

    record Category(UUID id, String name, int taskCount) {}
}
