package com.flowops.process.infrastructure.persistence;

import com.flowops.process.application.categorise.CategoryNameTakenException;
import com.flowops.process.application.categorise.CategoryNotFoundException;
import com.flowops.process.application.categorise.ProcessCategoryStorePort;
import com.flowops.process.infrastructure.persistence.entity.ProcessCategoryJpaEntity;
import com.flowops.process.infrastructure.persistence.repository.ProcessCategoryJpaRepository;
import com.flowops.process.infrastructure.persistence.repository.ProcessInstanceJpaRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class ProcessCategoryPersistenceAdapter implements ProcessCategoryStorePort {
    private final ProcessCategoryJpaRepository categories;
    private final ProcessInstanceJpaRepository instances;

    public ProcessCategoryPersistenceAdapter(
            ProcessCategoryJpaRepository categories, ProcessInstanceJpaRepository instances) {
        this.categories = categories;
        this.instances = instances;
    }

    @Override
    public List<StoredCategory> categoriesIn(UUID workspaceId) {
        Map<UUID, Integer> runs = new HashMap<>();
        for (UUID categoryId : filingsIn(workspaceId).values()) {
            runs.merge(categoryId, 1, Integer::sum);
        }

        return categories.findByWorkspaceIdOrderByNameAsc(workspaceId).stream()
                .map(category -> new StoredCategory(
                        category.getId(), category.getName(), runs.getOrDefault(category.getId(), 0)))
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
    public boolean exists(UUID categoryId, UUID workspaceId) {
        return categories.findByIdAndWorkspaceId(categoryId, workspaceId).isPresent();
    }

    @Override
    public void create(UUID id, UUID workspaceId, String name, Instant at) {
        saveOrRefuse(new ProcessCategoryJpaEntity(id, workspaceId, name, at), name);
    }

    @Override
    public void rename(UUID categoryId, UUID workspaceId, String name) {
        ProcessCategoryJpaEntity category = mine(categoryId, workspaceId);
        category.setName(name);
        saveOrRefuse(category, name);
    }

    @Override
    public void delete(UUID categoryId, UUID workspaceId) {
        categories.delete(mine(categoryId, workspaceId));
    }

    @Override
    public void fileRun(UUID instanceId, UUID workspaceId, UUID categoryId) {
        instances
                .findById(instanceId)
                .filter(run -> run.getWorkspaceId().equals(workspaceId))
                .orElseThrow(CategoryNotFoundException::new)
                .setCategoryId(categoryId);
    }

    private ProcessCategoryJpaEntity mine(UUID categoryId, UUID workspaceId) {
        return categories.findByIdAndWorkspaceId(categoryId, workspaceId).orElseThrow(CategoryNotFoundException::new);
    }

    private void saveOrRefuse(ProcessCategoryJpaEntity category, String name) {
        try {
            categories.saveAndFlush(category);
        } catch (DataIntegrityViolationException lost) {
            throw new CategoryNameTakenException(name);
        }
    }
}
