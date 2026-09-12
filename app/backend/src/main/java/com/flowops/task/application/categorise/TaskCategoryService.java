package com.flowops.task.application.categorise;

import com.flowops.shared.domain.GroupingName;
import com.flowops.task.application.shared.port.WorkspacePort;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskCategoryService implements TaskCategoryUseCase {
    private final TaskCategoryStorePort store;
    private final WorkspacePort workspace;
    private final Clock clock;

    public TaskCategoryService(TaskCategoryStorePort store, WorkspacePort workspace, Clock clock) {
        this.store = store;
        this.workspace = workspace;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('TASK_VIEW_OWN')")
    public List<Category> all() {
        return store.categoriesIn(workspace.currentWorkspaceId()).stream()
                .map(stored -> new Category(stored.id(), stored.name(), stored.taskCount()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('TASK_VIEW_OWN')")
    public Map<UUID, UUID> filings() {
        return store.filingsIn(workspace.currentWorkspaceId());
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('WORKSPACE_CONFIGURE')")
    public UUID create(String name) {
        GroupingName called = GroupingName.of(name);
        UUID here = workspace.currentWorkspaceId();
        refuseIfTaken(here, called, null);

        UUID id = UUID.randomUUID();
        store.create(id, here, called.value(), clock.instant());
        return id;
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('WORKSPACE_CONFIGURE')")
    public void rename(UUID categoryId, String name) {
        GroupingName called = GroupingName.of(name);
        UUID here = workspace.currentWorkspaceId();
        mustBeMine(categoryId, here);
        refuseIfTaken(here, called, categoryId);

        store.rename(categoryId, here, called.value());
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('WORKSPACE_CONFIGURE')")
    public void delete(UUID categoryId) {
        store.delete(categoryId, workspace.currentWorkspaceId());
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('TASK_EDIT')")
    public void fileTask(UUID taskId, Optional<UUID> categoryId) {
        UUID here = workspace.currentWorkspaceId();

        categoryId.ifPresent(id -> mustBeMine(id, here));

        store.fileTask(taskId, here, categoryId.orElse(null));
    }

    private void mustBeMine(UUID categoryId, UUID workspaceId) {
        if (!store.exists(categoryId, workspaceId)) {
            throw new CategoryNotFoundException();
        }
    }

    private void refuseIfTaken(UUID workspaceId, GroupingName name, UUID exceptThisOne) {
        boolean taken = store.categoriesIn(workspaceId).stream()
                .filter(other -> !other.id().equals(exceptThisOne))
                .anyMatch(other -> name.sameAs(GroupingName.of(other.name())));
        if (taken) {
            throw new CategoryNameTakenException(name.value());
        }
    }
}
