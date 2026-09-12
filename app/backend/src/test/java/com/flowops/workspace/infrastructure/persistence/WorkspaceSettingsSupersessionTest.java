package com.flowops.workspace.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.workspace.WorkspaceIntegrationTest;
import com.flowops.workspace.application.shared.port.LoadWorkspacePort;
import com.flowops.workspace.application.shared.port.SaveWorkspaceSettingsPort;
import com.flowops.workspace.domain.model.Timezone;
import com.flowops.workspace.domain.model.WorkspaceId;
import com.flowops.workspace.domain.model.WorkspaceSettings;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("WORKSPACE-SETUP-01")
class WorkspaceSettingsSupersessionTest extends WorkspaceIntegrationTest {
    @Autowired
    private SaveWorkspaceSettingsPort saveSettings;

    @Autowired
    private LoadWorkspacePort loadWorkspace;

    @Test
    void asecondRowClosesTheFirstRatherThanCollidingWithIt() {
        WorkspaceId workspace = loadWorkspace.load().id();
        Instant first = Instant.parse("2026-08-03T09:00:00Z");
        Instant second = Instant.parse("2026-09-01T09:00:00Z");

        saveSettings.save(WorkspaceSettings.inForceFrom(workspace, new Timezone("Europe/Bucharest"), first));
        saveSettings.save(WorkspaceSettings.inForceFrom(workspace, new Timezone("Europe/London"), second));

        assertThat(settingsRows()).hasSize(2);
        assertThat(settingsRows().stream().filter(row -> row.get("effective_to") == null))
                .singleElement()
                .extracting(row -> row.get("timezone"))
                .isEqualTo("Europe/London");
    }

    @Test
    void theSupersededRowIsClosedAtTheInstantTheNewOneOpens() {
        WorkspaceId workspace = loadWorkspace.load().id();
        Instant first = Instant.parse("2026-08-03T09:00:00Z");
        Instant second = Instant.parse("2026-09-01T09:00:00Z");

        saveSettings.save(WorkspaceSettings.inForceFrom(workspace, new Timezone("Europe/Bucharest"), first));
        saveSettings.save(WorkspaceSettings.inForceFrom(workspace, new Timezone("Europe/London"), second));

        Map<String, Object> closed = settingsRows().stream()
                .filter(row -> row.get("effective_to") != null)
                .findFirst()
                .orElseThrow();
        assertThat(closed.get("timezone")).isEqualTo("Europe/Bucharest");
        assertThat(closed.get("effective_to").toString()).isNotBlank();
    }
}
