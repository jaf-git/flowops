package com.flowops.workspace.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.workspace.domain.enums.WorkspaceUse;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("WORKSPACE-SETUP-01")
class WorkspaceTest {
    private static final WorkspaceId ID = WorkspaceId.of(UUID.randomUUID());
    private static final Instant SEEDED_AT = Instant.parse("2026-08-01T09:00:00Z");

    private static Workspace seeded() {
        return Workspace.rebuild(ID, null, null, SEEDED_AT);
    }

    @Test
    void aSeededWorkspaceHasNoNameAndNoUse() {
        Workspace workspace = seeded();

        assertThat(workspace.isNamed()).isFalse();
        assertThat(workspace.name()).isEmpty();
        assertThat(workspace.use()).isEmpty();
    }

    @Test
    void namingItRecordsBothTheNameAndTheUse() {
        Workspace named = seeded().named(new WorkspaceName("Atelier Ionescu"), WorkspaceUse.WORK);

        assertThat(named.isNamed()).isTrue();
        assertThat(named.name()).contains(new WorkspaceName("Atelier Ionescu"));
        assertThat(named.use()).contains(WorkspaceUse.WORK);
    }

    @Test
    void namingItLeavesTheIdentityAndTheCreationInstantAlone() {
        Workspace named = seeded().named(new WorkspaceName("Atelier Ionescu"), WorkspaceUse.PERSONAL);

        assertThat(named.id()).isEqualTo(ID);
        assertThat(named.createdAt()).isEqualTo(SEEDED_AT);
    }

    @Test
    void namingItDoesNotChangeTheWorkspaceItWasCalledOn() {
        Workspace before = seeded();

        before.named(new WorkspaceName("Atelier Ionescu"), WorkspaceUse.WORK);

        assertThat(before.isNamed()).isFalse();
    }
}
