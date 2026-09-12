package com.flowops.task.application.categorise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowops.task.application.shared.port.WorkspacePort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("TASK-CATEGORISE-TASKS-01")
@ExtendWith(MockitoExtension.class)
class TaskCategoryServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-22T09:00:00Z");
    private static final UUID HERE = UUID.randomUUID();
    private static final UUID AURORA = UUID.randomUUID();
    private static final UUID A_TASK = UUID.randomUUID();

    @Mock
    private TaskCategoryStorePort store;

    @Mock
    private WorkspacePort workspace;

    private TaskCategoryService service;

    @BeforeEach
    void buildTheService() {
        service = new TaskCategoryService(store, workspace, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void aNameIsStoredWithoutTheSpaceSomebodyTypedAroundIt() {
        inTheWorkspace();
        when(store.categoriesIn(HERE)).thenReturn(List.of());

        service.create("  Aurora Coffee  ");

        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(store).create(any(UUID.class), eq(HERE), stored.capture(), eq(NOW));
        assertThat(stored.getValue()).isEqualTo("Aurora Coffee");
    }

    @Test
    void aNameThatDiffersOnlyByCaseOrSpaceIsRefusedBeforeTheWriteIsAttempted() {
        inTheWorkspace();
        when(store.categoriesIn(HERE))
                .thenReturn(List.of(new TaskCategoryStorePort.StoredCategory(AURORA, "Aurora Coffee", 3)));

        assertThatThrownBy(() -> service.create(" aurora COFFEE "))
                .isInstanceOf(CategoryNameTakenException.class)
                .hasMessageContaining("aurora COFFEE");

        verify(store, never()).create(any(), any(), any(), any());
    }

    @Test
    void aGroupingMayBeRenamedToADifferentCasingOfItsOwnName() {
        inTheWorkspace();
        when(store.exists(AURORA, HERE)).thenReturn(true);
        when(store.categoriesIn(HERE))
                .thenReturn(List.of(new TaskCategoryStorePort.StoredCategory(AURORA, "aurora coffee", 3)));

        service.rename(AURORA, "Aurora Coffee");

        verify(store).rename(AURORA, HERE, "Aurora Coffee");
    }

    @Test
    void filingUnderAGroupingFromSomewhereElseTouchesTheTaskNotAtAll() {
        inTheWorkspace();
        when(store.exists(AURORA, HERE)).thenReturn(false);

        assertThatThrownBy(() -> service.fileTask(A_TASK, Optional.of(AURORA)))
                .isInstanceOf(CategoryNotFoundException.class);

        verify(store, never()).fileTask(any(), any(), any());
    }

    @Test
    void filingUnderNothingClearsWhateverTheTaskWasIn() {
        inTheWorkspace();

        service.fileTask(A_TASK, Optional.empty());

        verify(store).fileTask(A_TASK, HERE, null);
        verify(store, never()).exists(any(), any());
    }

    @Test
    void removingAGroupingAsksForNothingToBeDoneToItsTasks() {
        inTheWorkspace();

        service.delete(AURORA);

        verify(store).delete(AURORA, HERE);
        verify(store, never()).fileTask(any(), any(), any());
    }

    private void inTheWorkspace() {
        when(workspace.currentWorkspaceId()).thenReturn(HERE);
    }
}
