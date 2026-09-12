package com.flowops.workspace.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.workspace.domain.exception.WorkspaceNameRequiredException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("WORKSPACE-SETUP-01")
class WorkspaceNameTest {
    @Test
    void itKeepsWhatTheOwnerTyped() {
        assertThat(new WorkspaceName("Atelier Ionescu").value()).isEqualTo("Atelier Ionescu");
    }

    @Test
    void surroundingSpaceIsTrimmedRatherThanStored() {
        assertThat(new WorkspaceName("  Atelier Ionescu  ").value()).isEqualTo("Atelier Ionescu");
    }

    @Test
    void anEmptyNameIsRefused() {
        assertThatThrownBy(() -> new WorkspaceName("")).isInstanceOf(WorkspaceNameRequiredException.class);
    }

    @Test
    void aNameOfNothingButSpaceIsRefused() {
        assertThatThrownBy(() -> new WorkspaceName("   ")).isInstanceOf(WorkspaceNameRequiredException.class);
    }

    @Test
    void aNameLongerThanTheColumnIsRefusedRatherThanTruncated() {
        String tooLong = "a".repeat(WorkspaceName.MAXIMUM_LENGTH + 1);

        assertThatThrownBy(() -> new WorkspaceName(tooLong)).isInstanceOf(WorkspaceNameRequiredException.class);
    }

    @Test
    void aRomanianNameIsAccepted() {
        assertThat(new WorkspaceName("Tâmplăria Ionuț și Frații").value()).isEqualTo("Tâmplăria Ionuț și Frații");
    }
}
