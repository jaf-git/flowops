package com.flowops.task.application.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.flowops.task.application.shared.port.WorkspaceSettingsPort;
import com.flowops.task.domain.enums.TaskPriority;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.Task;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("TASK-EDIT-01")
@ExtendWith(MockitoExtension.class)
class AtRiskRuleTest {
    private static final Instant NOW = Instant.parse("2026-08-12T09:00:00Z");
    private static final PersonId MARIA = PersonId.of(UUID.randomUUID());
    private static final PersonId ANDREI = PersonId.of(UUID.randomUUID());

    @Mock
    private WorkspaceSettingsPort workspaceSettingsPort;

    @Test
    void theWindowIsReadAgainEveryTimeRatherThanRemembered() {
        Task task = work(NOW.plusSeconds(3 * 24 * 3600));
        when(workspaceSettingsPort.atRiskWindowHours()).thenReturn(24, 24 * 7);

        AtRiskRule rule = rule();

        assertThat(rule.of(task)).isFalse();
        assertThat(rule.of(task)).isTrue();
    }

    @Test
    void workBecomesAtRiskWithNobodyHavingTouchedIt() {
        Task untouched = work(NOW.plusSeconds(48 * 3600));

        AtRiskRule rule = rule();

        when(workspaceSettingsPort.atRiskWindowHours()).thenReturn(24);
        assertThat(rule.of(untouched))
                .as("two days out, against a one-day window")
                .isFalse();

        when(workspaceSettingsPort.atRiskWindowHours()).thenReturn(72);
        assertThat(rule.of(untouched))
                .as("the same task, unmodified, against the window the owner has just widened")
                .isTrue();
    }

    @Test
    void workAlreadySubmittedIsNeverAtRisk() {
        Task completed = work(NOW.plusSeconds(3600)).accepted().started().completed();
        lenient().when(workspaceSettingsPort.atRiskWindowHours()).thenReturn(24 * 30);

        assertThat(rule().of(completed)).isFalse();
    }

    @Test
    void workNobodyHasPutADateOnIsNeverAtRisk() {
        lenient().when(workspaceSettingsPort.atRiskWindowHours()).thenReturn(24 * 30);

        assertThat(rule().of(work(null))).isFalse();
    }

    private AtRiskRule rule() {
        return new AtRiskRule(workspaceSettingsPort, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Task work(Instant deadline) {
        return Task.given(
                "Draft the supplier review",
                "Compare last quarter against this one.",
                ANDREI,
                MARIA,
                deadline,
                TaskPriority.NORMAL,
                NOW);
    }
}
