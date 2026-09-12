package com.flowops.task.infrastructure.persistence;

import com.flowops.task.application.categorise.CategoryNameTakenException;
import com.flowops.task.application.categorise.CategoryNotFoundException;
import com.flowops.task.application.categorise.TaskCategoryStorePort;
import com.flowops.task.application.shared.port.TaskCategoryFilingsPort;
import com.flowops.task.infrastructure.persistence.entity.TaskCategoryJpaEntity;
import com.flowops.task.infrastructure.persistence.repository.TaskCategoryJpaRepository;
import com.flowops.task.infrastructure.persistence.repository.TaskJpaRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class TaskCategoryPersistenceAdapter implements TaskCategoryStorePort, TaskCategoryFilingsPort {
    private final TaskCategoryJpaRepository categories;
    private final TaskJpaRepository tasks;

    public TaskCategoryPersistenceAdapter(TaskCategoryJpaRepository categories, TaskJpaRepository tasks) {
        this.categories = categories;
        this.tasks = tasks;
    }

    @Override
    public List<StoredCategory> categoriesIn(UUID workspaceId) {
        Map<UUID, Integer> counts = new HashMap<>();
        for (UUID categoryId : filingsIn(workspaceId).values()) {
            counts.merge(categoryId, 1, Integer::sum);
        }

        return categories.findByWorkspaceIdOrderByNameAsc(workspaceId).stream()
                .map(category -> new StoredCategory(
                        category.getId(), category.getName(), counts.getOrDefault(category.getId(), 0)))
                .toList();
    }

    @Override
    public Map<UUID, UUID> filingsIn(UUID workspaceId) {
        Map<UUID, UUID> filed = new HashMap<>();
        for (Object[] row : categories.filingsIn(workspaceId)) {
            filed.put((UUID) row[0], (UUID) row[1]);
        }
        return filed;
    }

    @Override
    public Map<UUID, Filing> filingsOf(Collection<UUID> taskIds) {
        if (taskIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Filing> filed = new HashMap<>();
        for (Object[] row : categories.filingsOf(taskIds)) {
            filed.put((UUID) row[0], new Filing((UUID) row[1], (String) row[2]));
        }
        return filed;
    }

    @Override
    public boolean exists(UUID categoryId, UUID workspaceId) {
        return categories.findByIdAndWorkspaceId(categoryId, workspaceId).isPresent();
    }

    @Override
    public void create(UUID id, UUID workspaceId, String name, Instant at) {
        saveOrRefuse(new TaskCategoryJpaEntity(id, workspaceId, name, at), name);
    }

    @Override
    public void rename(UUID categoryId, UUID workspaceId, String name) {
        TaskCategoryJpaEntity category = mine(categoryId, workspaceId);
        category.setName(name);
        saveOrRefuse(category, name);
    }

    @Override
    public void delete(UUID categoryId, UUID workspaceId) {
        categories.delete(mine(categoryId, workspaceId));
    }

    @Override
    public void fileTask(UUID taskId, UUID workspaceId, UUID categoryId) {
        tasks.findById(taskId)
                .filter(task -> task.getWorkspaceId().equals(workspaceId))
                .orElseThrow(CategoryNotFoundException::new)
                .setCategoryId(categoryId);
    }

    private TaskCategoryJpaEntity mine(UUID categoryId, UUID workspaceId) {
        return categories.findByIdAndWorkspaceId(categoryId, workspaceId).orElseThrow(CategoryNotFoundException::new);
    }

    private void saveOrRefuse(TaskCategoryJpaEntity category, String name) {
        try {
            categories.saveAndFlush(category);
        } catch (DataIntegrityViolationException lost) {
            throw new CategoryNameTakenException(name);
        }
    }
}
