package com.flowops.workspace.application.shared.port;

import com.flowops.workspace.domain.model.WorkspaceId;
import java.util.List;
import java.util.UUID;

public interface SaveSettingsChangePort {
    void record(UUID eventId, WorkspaceId workspace, List<FieldChange> changes);

    record FieldChange(String field, String oldValue, String newValue) {}
}
