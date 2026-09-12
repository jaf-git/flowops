package com.flowops.auth.infrastructure.persistence.repository;

import com.flowops.auth.infrastructure.persistence.entity.AuthUserJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthUserJpaRepository extends JpaRepository<AuthUserJpaEntity, UUID> {
    Optional<AuthUserJpaEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRoleName(String roleName);

    @Query(
            value =
                    """
                    select rolePermission.permission_name
                      from auth_role_permission rolePermission
                      join auth_user holder on holder.role_name = rolePermission.role_name
                     where holder.id = :userId
                     union
                    select granted.permission_name
                      from auth_user_permission granted
                     where granted.user_id = :userId
                    """,
            nativeQuery = true)
    List<String> findPermissionNames(@Param("userId") UUID userId);
}
