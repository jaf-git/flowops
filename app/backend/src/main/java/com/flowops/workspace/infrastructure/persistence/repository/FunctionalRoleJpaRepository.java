package com.flowops.workspace.infrastructure.persistence.repository;

import com.flowops.workspace.infrastructure.persistence.entity.FunctionalRoleJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FunctionalRoleJpaRepository extends JpaRepository<FunctionalRoleJpaEntity, UUID> {
    List<FunctionalRoleJpaEntity> findAllByOrderByDisplayOrderAsc();

    @Query("select r from FunctionalRoleJpaEntity r where lower(r.name) = lower(:name)")
    Optional<FunctionalRoleJpaEntity> findByNameIgnoringCase(@Param("name") String name);
}
