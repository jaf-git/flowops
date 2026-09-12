package com.flowops.task.application.viewtasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.flowops.task.domain.enums.PhaseKind;
import com.flowops.task.domain.enums.TaskKind;
import com.flowops.task.domain.enums.TaskPriority;
import com.flowops.task.domain.enums.TaskState;
import com.flowops.task.domain.model.TaskId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SectionedTaskQueryServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-20T09:00:00Z");

    @Mock
    private ViewTasksUseCase viewTasks;

    private SectionedTaskQueryService service;

    @BeforeEach
    void setUp() {
        service = new SectionedTaskQueryService(viewTasks, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void everySectionCountSumsToTheTotal() {
        given(oneOfEveryShapeThisProductCanProduce());

        SectionedTasksResult.Counts counts = service.count(TaskFilter.none());

        int summed = counts.sections().stream()
                .mapToInt(SectionedTasksResult.SectionCount::count)
                .sum();
        assertThat(summed).isEqualTo(counts.total());
        assertThat(counts.total())
                .isEqualTo(oneOfEveryShapeThisProductCanProduce().size());
    }

    @Test
    void everyBandIsReportedEvenWhenItIsEmpty() {
        given(List.of(row(TaskState.IN_PROGRESS)));

        SectionedTasksResult.Counts counts = service.count(TaskFilter.none());

        assertThat(counts.sections()).hasSize(TaskSection.values().length);
        assertThat(counts.sections().stream().map(SectionedTasksResult.SectionCount::section))
                .containsExactly(TaskSection.values());
    }

    @Test
    void aClosedTaskIsInTheArchiveAndInNothingElse() {
        ViewTasksResult.Row closed = withDeadline(row(TaskState.CLOSED), NOW.minusSeconds(86_400));
        given(List.of(closed));

        SectionedTasksResult.Counts counts = service.count(TaskFilter.none());

        assertThat(countOf(counts, TaskSection.ARCHIVE)).isEqualTo(1);
        assertThat(countOf(counts, TaskSection.OVERDUE)).isZero();
    }

    @Test
    void latenessOutranksBeingUnderway() {
        given(List.of(withDeadline(row(TaskState.IN_PROGRESS), NOW.minusSeconds(3_600))));

        SectionedTasksResult.Counts counts = service.count(TaskFilter.none());

        assertThat(countOf(counts, TaskSection.OVERDUE)).isEqualTo(1);
        assertThat(countOf(counts, TaskSection.IN_PROGRESS)).isZero();
    }

    @Test
    void aDateProposalSitsWithWhoeverHasToAnswerIt() {
        ViewTasksResult.Row waitingOnMe = proposal(true, false);
        ViewTasksResult.Row waitingOnThem = proposal(false, true);
        given(List.of(waitingOnMe, waitingOnThem));

        SectionedTasksResult.Counts counts = service.count(TaskFilter.none());

        assertThat(countOf(counts, TaskSection.NEEDS_YOU)).isEqualTo(1);
        assertThat(countOf(counts, TaskSection.WAITING)).isEqualTo(1);
    }

    @Test
    void theFilterIsAppliedBeforeTheSectionsAreCounted() {
        given(List.of(titled("Verificare factura Aprilie"), titled("Livrare comanda"), titled("Verificare stoc")));

        SectionedTasksResult.Counts counts = service.count(new TaskFilter("verificare", null, null, null, null, null));

        assertThat(counts.total()).isEqualTo(2);
    }

    @Test
    void noRowAppearsOnTwoPagesAndNoneIsLost() {
        given(distinctTasksInOneBand(25));

        List<String> firstPage = idsOn(0, 10);
        List<String> secondPage = idsOn(1, 10);
        List<String> lastPage = idsOn(2, 10);

        assertThat(firstPage).hasSize(10);
        assertThat(secondPage).hasSize(10);
        assertThat(lastPage).hasSize(5);
        List<String> everything = new ArrayList<>(firstPage);
        everything.addAll(secondPage);
        everything.addAll(lastPage);
        assertThat(everything).doesNotHaveDuplicates().hasSize(25);
    }

    @Test
    void aPagePastTheEndIsEmptyRatherThanAFailure() {
        given(distinctTasksInOneBand(3));

        SectionedTasksResult.Page page =
                service.page(TaskSection.NOT_STARTED, TaskFilter.none(), TaskSort.DEADLINE, 9, 10);

        assertThat(page.rows()).isEmpty();
        assertThat(page.total()).isEqualTo(3);
    }

    @Test
    void anEmptySectionStillReadsAsOnePage() {
        given(List.of(row(TaskState.IN_PROGRESS)));

        SectionedTasksResult.Page page = service.page(TaskSection.OVERDUE, TaskFilter.none(), TaskSort.DEADLINE, 0, 25);

        assertThat(page.rows()).isEmpty();
        assertThat(page.totalPages()).isEqualTo(1);
    }

    @Test
    void sortingByPriorityPutsTheMostUrgentFirst() {
        given(List.of(
                prioritised(TaskPriority.LOW), prioritised(TaskPriority.URGENT), prioritised(TaskPriority.NORMAL)));

        SectionedTasksResult.Page page =
                service.page(TaskSection.NOT_STARTED, TaskFilter.none(), TaskSort.PRIORITY, 0, 25);

        assertThat(page.rows().stream().map(ViewTasksResult.Row::priority))
                .containsExactly(TaskPriority.URGENT, TaskPriority.NORMAL, TaskPriority.LOW);
    }

    private List<String> idsOn(int page, int size) {
        return service.page(TaskSection.NOT_STARTED, TaskFilter.none(), TaskSort.DEADLINE, page, size).rows().stream()
                .map(row -> row.id().value().toString())
                .toList();
    }

    private void given(List<ViewTasksResult.Row> rows) {
        when(viewTasks.execute()).thenReturn(new ViewTasksResult(rows));
    }

    private int countOf(SectionedTasksResult.Counts counts, TaskSection section) {
        return counts.sections().stream()
                .filter(entry -> entry.section() == section)
                .findFirst()
                .orElseThrow()
                .count();
    }

    private List<ViewTasksResult.Row> oneOfEveryShapeThisProductCanProduce() {
        List<ViewTasksResult.Row> rows = new ArrayList<>();
        for (TaskState state : TaskState.values()) {
            rows.add(row(state));
            rows.add(withDeadline(row(state), NOW.minusSeconds(86_400)));
            rows.add(withDeadline(row(state), NOW.plusSeconds(86_400)));
            rows.add(withDeadline(row(state), NOW.plusSeconds(86_400L * 30)));
        }
        rows.add(proposal(true, false));
        rows.add(proposal(false, true));
        return rows;
    }

    private List<ViewTasksResult.Row> distinctTasksInOneBand(int howMany) {
        List<ViewTasksResult.Row> rows = new ArrayList<>();
        for (int index = 0; index < howMany; index++) {
            rows.add(row(TaskState.ACCEPTED));
        }
        return rows;
    }

    private ViewTasksResult.Row row(TaskState state) {
        return new ViewTasksResult.Row(
                new TaskId(UUID.randomUUID()),
                "Verificare factura",
                UUID.randomUUID(),
                "Maria Popescu",
                null,
                TaskPriority.NORMAL,
                state,
                PhaseKind.ACTIVE,
                NOW.minusSeconds(3_600),
                false,
                false,
                false,
                false,
                TaskKind.TASK,
                null);
    }

    private ViewTasksResult.Row withDeadline(ViewTasksResult.Row row, Instant deadline) {
        return new ViewTasksResult.Row(
                row.id(),
                row.title(),
                row.assigneeId(),
                row.assigneeName(),
                deadline,
                row.priority(),
                row.state(),
                row.openPhase(),
                row.phaseSince(),
                row.mine(),
                row.directedByMe(),
                row.deadlineProposalOpen(),
                row.atRisk(),
                row.kind(),
                row.templateId());
    }

    private ViewTasksResult.Row titled(String title) {
        ViewTasksResult.Row base = row(TaskState.ACCEPTED);
        return new ViewTasksResult.Row(
                base.id(),
                title,
                base.assigneeId(),
                base.assigneeName(),
                base.deadline(),
                base.priority(),
                base.state(),
                base.openPhase(),
                base.phaseSince(),
                base.mine(),
                base.directedByMe(),
                base.deadlineProposalOpen(),
                base.atRisk(),
                base.kind(),
                base.templateId());
    }

    private ViewTasksResult.Row prioritised(TaskPriority priority) {
        ViewTasksResult.Row base = row(TaskState.ACCEPTED);
        return new ViewTasksResult.Row(
                base.id(),
                base.title(),
                base.assigneeId(),
                base.assigneeName(),
                null,
                priority,
                base.state(),
                base.openPhase(),
                base.phaseSince(),
                base.mine(),
                base.directedByMe(),
                base.deadlineProposalOpen(),
                base.atRisk(),
                base.kind(),
                base.templateId());
    }

    private ViewTasksResult.Row proposal(boolean directedByMe, boolean mine) {
        ViewTasksResult.Row base = row(TaskState.ACCEPTED);
        return new ViewTasksResult.Row(
                base.id(),
                base.title(),
                base.assigneeId(),
                base.assigneeName(),
                null,
                base.priority(),
                base.state(),
                base.openPhase(),
                base.phaseSince(),
                mine,
                directedByMe,
                true,
                base.atRisk(),
                base.kind(),
                base.templateId());
    }
}
