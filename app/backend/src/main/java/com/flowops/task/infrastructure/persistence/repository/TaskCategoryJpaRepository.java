package com.flowops.task.infrastructure.persistence.repository;

import com.flowops.task.infrastructure.persistence.entity.TaskCategoryJpaEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskCategoryJpaRepository extends JpaRepository<TaskCategoryJpaEntity, UUID> {
    List<TaskCategoryJpaEntity> findByWorkspaceIdOrderByNameAsc(UUID workspaceId);

    Optional<TaskCategoryJpaEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    @Query(
            """
            select t.id, t.categoryId
            from TaskJpaEntity t
            where t.workspaceId = :workspaceId and t.categoryId is not null
            """)
    List<Object[]> filingsIn(@Param("workspaceId") UUID workspaceId);

    @Query(
            """
            select t.id, c.id, c.name
            from TaskJpaEntity t join TaskCategoryJpaEntity c on c.id = t.categoryId
            where t.id in :taskIds
            """)
    List<Object[]> filingsOf(@Param("taskIds") Collection<UUID> taskIds);
}
