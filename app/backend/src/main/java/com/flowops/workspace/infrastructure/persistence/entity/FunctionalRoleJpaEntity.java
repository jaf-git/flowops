package com.flowops.workspace.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "functional_role")
public class FunctionalRoleJpaEntity {
    @Id
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FunctionalRoleJpaEntity() {}

    public FunctionalRoleJpaEntity(UUID id, String name, UUID departmentId, int displayOrder, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.departmentId = departmentId;
        this.displayOrder = displayOrder;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
