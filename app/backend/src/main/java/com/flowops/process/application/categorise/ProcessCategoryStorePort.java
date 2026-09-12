package com.flowops.process.application.categorise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ProcessCategoryStorePort {
    record StoredCategory(UUID id, String name, int runCount) {}

    List<StoredCategory> categoriesIn(UUID workspaceId);

    Map<UUID, UUID> filingsIn(UUID workspaceId);

    boolean exists(UUID categoryId, UUID workspaceId);

    void create(UUID id, UUID workspaceId, String name, Instant at);

    void rename(UUID categoryId, UUID workspaceId, String name);

    void delete(UUID categoryId, UUID workspaceId);

    void fileRun(UUID instanceId, UUID workspaceId, UUID categoryId);
}
