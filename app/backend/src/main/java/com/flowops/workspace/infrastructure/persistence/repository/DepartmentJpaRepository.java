package com.flowops.workspace.infrastructure.persistence.repository;

import com.flowops.workspace.infrastructure.persistence.entity.DepartmentJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentJpaRepository extends JpaRepository<DepartmentJpaEntity, UUID> {
    List<DepartmentJpaEntity> findAllByOrderByDisplayOrderAsc();
}
