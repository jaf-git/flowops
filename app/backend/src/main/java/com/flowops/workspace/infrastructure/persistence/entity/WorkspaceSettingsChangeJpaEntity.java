package com.flowops.workspace.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "workspace_settings_change")
public class WorkspaceSettingsChangeJpaEntity {
    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "field", nullable = false)
    private String field;

    @Column(name = "old_value")
    private String oldValue;

    @Column(name = "new_value", nullable = false)
    private String newValue;

    protected WorkspaceSettingsChangeJpaEntity() {}

    public WorkspaceSettingsChangeJpaEntity(UUID id, UUID eventId, String field, String oldValue, String newValue) {
        this.id = id;
        this.eventId = eventId;
        this.field = field;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getField() {
        return field;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }
}
