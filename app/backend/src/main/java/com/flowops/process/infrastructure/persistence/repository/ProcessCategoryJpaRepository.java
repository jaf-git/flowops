package com.flowops.process.infrastructure.persistence.repository;

import com.flowops.process.infrastructure.persistence.entity.ProcessCategoryJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessCategoryJpaRepository extends JpaRepository<ProcessCategoryJpaEntity, UUID> {
    List<ProcessCategoryJpaEntity> findByWorkspaceIdOrderByNameAsc(UUID workspaceId);

    Optional<ProcessCategoryJpaEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    @Query(
            """
            select i.id, i.categoryId
            from ProcessInstanceJpaEntity i
            where i.workspaceId = :workspaceId and i.categoryId is not null
            """)
    List<Object[]> filingsIn(@Param("workspaceId") UUID workspaceId);
}
