package com.flowops.task.application.viewtaskdetail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowops.task.application.shared.AtRiskRule;
import com.flowops.task.application.shared.TaskReviewSupport;
import com.flowops.task.application.shared.TaskWriter;
import com.flowops.task.application.shared.exception.TaskNotFoundException;
import com.flowops.task.application.shared.exception.TaskOutOfScopeException;
import com.flowops.task.application.shared.port.CallerPermissionsPort;
import com.flowops.task.application.shared.port.IdentifyCallerPort;
import com.flowops.task.application.shared.port.LoadApprovalPort;
import com.flowops.task.application.shared.port.LoadCompletionProofPort;
import com.flowops.task.application.shared.port.LoadDeadlineProposalPort;
import com.flowops.task.application.shared.port.LoadPersonPort;
import com.flowops.task.application.shared.port.LoadTaskPort;
import com.flowops.task.application.shared.port.PhaseTimerPort;
import com.flowops.task.application.shared.port.ReportingLinePort;
import com.flowops.task.domain.enums.PhaseKind;
import com.flowops.task.domain.enums.TaskPriority;
import com.flowops.task.domain.model.CompletionProof;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.PhaseTimer;
import com.flowops.task.domain.model.PhaseTimerId;
import com.flowops.task.domain.model.Task;
import com.flowops.task.domain.model.TaskId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("TASK-REVIEW-01")
@ExtendWith(MockitoExtension.class)
class ViewTaskDetailServiceTest {
    private static final Instant MONDAY_09 = Instant.parse("2026-08-10T09:00:00Z");
    private static final Instant MONDAY_11 = Instant.parse("2026-08-10T11:00:00Z");
    private static final Instant MONDAY_15 = Instant.parse("2026-08-10T15:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-10T17:00:00Z");
    private static final PersonId MARIA = PersonId.of(UUID.randomUUID());
    private static final PersonId IONUT = PersonId.of(UUID.randomUUID());
    private static final PersonId ANDREI = PersonId.of(UUID.randomUUID());

    @Mock
    private IdentifyCallerPort identifyCallerPort;

    @Mock
    private CallerPermissionsPort callerPermissionsPort;

    @Mock
    private ReportingLinePort reportingLinePort;

    @Mock
    private LoadTaskPort loadTaskPort;

    @Mock
    private PhaseTimerPort phaseTimerPort;

    @Mock
    private LoadCompletionProofPort loadCompletionProofPort;

    @Mock
    private LoadApprovalPort loadApprovalPort;

    @Mock
    private LoadPersonPort loadPersonPort;

    @Mock
    private TaskWriter taskWriter;

    @Mock
    private LoadDeadlineProposalPort loadDeadlineProposalPort;

    private ViewTaskDetailService service;

    @BeforeEach
    void buildTheService() {
        service = new ViewTaskDetailService(
                identifyCallerPort,
                loadTaskPort,
                phaseTimerPort,
                loadCompletionProofPort,
                loadApprovalPort,
                loadPersonPort,
                loadDeadlineProposalPort,
                reviews(),
                new AtRiskRule(() -> 24, Clock.fixed(NOW, ZoneOffset.UTC)),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void theDetailCarriesWhatWasDeliveredAndWhoItBelongsTo() {
        Task completed = completed();
        ionutOpens(completed);
        theProofIs(CompletionProof.of(completed.id(), "Compared both quarters.", null, MONDAY_15));

        ViewTaskDetailResult detail = service.execute(completed.id());

        assertThat(detail.task().id()).isEqualTo(completed.id());
        assertThat(detail.assigneeName()).isEqualTo("Andrei Munteanu");
        assertThat(detail.proof().note()).isEqualTo("Compared both quarters.");
        assertThat(detail.approval()).isNull();
    }

    @Test
    void everyIntervalIsSummedByItsOwnKindAndNothingIsAddedAcross() {
        Task completed = completed();
        ionutOpens(completed);
        theProofIs(CompletionProof.of(completed.id(), "Done.", null, MONDAY_15));
        thePhasesAre(
                closed(completed.id(), PhaseKind.WAIT, MONDAY_09, MONDAY_11),
                closed(completed.id(), PhaseKind.ACTIVE, MONDAY_11, MONDAY_11.plusSeconds(3_600)),
                closed(completed.id(), PhaseKind.ACTIVE, MONDAY_11.plusSeconds(7_200), MONDAY_15),
                open(completed.id(), PhaseKind.REVIEW, MONDAY_15));

        ViewTaskDetailResult detail = service.execute(completed.id());

        assertThat(detail.phases())
                .extracting(ViewTaskDetailResult.PhaseSpan::kind)
                .containsExactlyInAnyOrder(PhaseKind.WAIT, PhaseKind.ACTIVE, PhaseKind.REVIEW);
        assertThat(secondsOf(detail, PhaseKind.WAIT)).as("09:00 to 11:00").isEqualTo(7_200);
        assertThat(secondsOf(detail, PhaseKind.ACTIVE))
                .as("11:00 to 12:00, then 13:00 to 15:00 — two rows, summed, never averaged")
                .isEqualTo(3_600 + 7_200);
    }

    @Test
    void theIntervalStillRunningIsMeasuredToNowAndNamedAsOpen() {
        Task completed = completed();
        ionutOpens(completed);
        theProofIs(CompletionProof.of(completed.id(), "Done.", null, MONDAY_15));
        thePhasesAre(open(completed.id(), PhaseKind.REVIEW, MONDAY_15));

        ViewTaskDetailResult detail = service.execute(completed.id());

        assertThat(secondsOf(detail, PhaseKind.REVIEW)).isEqualTo(7_200);
        assertThat(detail.openPhase()).isEqualTo(PhaseKind.REVIEW);
        assertThat(detail.phaseSince()).isEqualTo(MONDAY_15);
    }

    @Test
    void workSubmittedBeforeItsDeadlineReadsAsMet() {
        Task completed = completed();
        ionutOpens(completed);
        theProofIs(CompletionProof.of(completed.id(), "Done.", null, MONDAY_15));
        thePhasesAre(open(completed.id(), PhaseKind.REVIEW, MONDAY_15));

        assertThat(service.execute(completed.id()).deadlineMet()).contains(true);
    }

    @Test
    void workSubmittedAfterItsDeadlineReadsAsMissed() {
        Task completed = completedWithDeadline(MONDAY_11);
        ionutOpens(completed);
        theProofIs(CompletionProof.of(completed.id(), "Late, but done.", null, MONDAY_15));
        thePhasesAre(open(completed.id(), PhaseKind.REVIEW, MONDAY_15));

        assertThat(service.execute(completed.id()).deadlineMet()).contains(false);
    }

    @Test
    void workThatHasNeverBeenSubmittedHasNoDeadlineOutcomeAtAll() {
        Task underway = task().accepted().started();
        ionutOpens(underway);
        when(loadCompletionProofPort.findByTask(underway.id())).thenReturn(Optional.empty());
        thePhasesAre(open(underway.id(), PhaseKind.ACTIVE, MONDAY_11));

        ViewTaskDetailResult detail = service.execute(underway.id());

        assertThat(detail.deadlineMet()).isEmpty();
        assertThat(detail.completedAt()).isNull();
        assertThat(detail.proof()).isNull();
    }

    @Test
    void readingATaskWritesNothing() {
        Task completed = completed();
        ionutOpens(completed);
        theProofIs(CompletionProof.of(completed.id(), "Done.", null, MONDAY_15));

        service.execute(completed.id());

        verifyNoInteractions(taskWriter);
    }

    @Test
    void aTaskOutsideMyReachIsRefused() {
        Task completed = completed();
        signedInAs(IONUT);
        when(callerPermissionsPort.callerHolds("TASK_VIEW_ANY")).thenReturn(false);
        when(reportingLinePort.subtreeOf(IONUT)).thenReturn(Set.of(MARIA));
        when(loadTaskPort.findById(completed.id())).thenReturn(Optional.of(completed));

        assertThatThrownBy(() -> service.execute(completed.id())).isInstanceOf(TaskOutOfScopeException.class);
    }

    @Test
    void aTaskThatDoesNotExistIsRefused() {
        TaskId missing = TaskId.generate();
        signedInAs(IONUT);
        when(loadTaskPort.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(missing)).isInstanceOf(TaskNotFoundException.class);
    }

    private void ionutOpens(Task task) {
        signedInAs(IONUT);
        when(callerPermissionsPort.callerHolds("TASK_VIEW_ANY")).thenReturn(true);
        when(loadTaskPort.findById(task.id())).thenReturn(Optional.of(task));
        when(loadApprovalPort.findByTask(task.id())).thenReturn(Optional.empty());
        when(loadPersonPort.describe(ANDREI))
                .thenReturn(Optional.of(new LoadPersonPort.Person(ANDREI, "Andrei Munteanu", true)));
    }

    private void signedInAs(PersonId person) {
        when(identifyCallerPort.currentCaller()).thenReturn(Optional.of(person));
    }

    private void theProofIs(CompletionProof proof) {
        when(loadCompletionProofPort.findByTask(proof.task())).thenReturn(Optional.of(proof));
    }

    private void thePhasesAre(PhaseTimer... phases) {
        when(phaseTimerPort.allPhasesOf(phases[0].task())).thenReturn(List.of(phases));
    }

    private static long secondsOf(ViewTaskDetailResult detail, PhaseKind kind) {
        return detail.phases().stream()
                .filter(span -> span.kind() == kind)
                .mapToLong(ViewTaskDetailResult.PhaseSpan::seconds)
                .sum();
    }

    private static PhaseTimer open(TaskId task, PhaseKind kind, Instant from) {
        return new PhaseTimer(PhaseTimerId.generate(), task, kind, from, null);
    }

    private static PhaseTimer closed(TaskId task, PhaseKind kind, Instant from, Instant to) {
        return new PhaseTimer(PhaseTimerId.generate(), task, kind, from, to);
    }

    private static Task task() {
        return completedWithDeadlineTask(MONDAY_15.plusSeconds(86_400));
    }

    private static Task completedWithDeadlineTask(Instant deadline) {
        return Task.given("Draft the supplier review", null, ANDREI, MARIA, deadline, TaskPriority.NORMAL, MONDAY_09);
    }

    private static Task completed() {
        return task().accepted().started().completed();
    }

    private static Task completedWithDeadline(Instant deadline) {
        return completedWithDeadlineTask(deadline).accepted().started().completed();
    }

    private TaskReviewSupport reviews() {
        return new TaskReviewSupport(
                identifyCallerPort,
                callerPermissionsPort,
                reportingLinePort,
                loadTaskPort,
                phaseTimerPort,
                loadPersonPort,
                taskWriter,
                new AtRiskRule(() -> 24, Clock.fixed(NOW, ZoneOffset.UTC)),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void workSubmittedTwiceIsJudgedOnTheLatestSubmission() {
        Task completed = completedWithDeadline(MONDAY_11);
        ionutOpens(completed);
        theProofIs(CompletionProof.of(completed.id(), "Done, at last.", null, MONDAY_15));
        thePhasesAre(
                closed(completed.id(), PhaseKind.REVIEW, MONDAY_09, MONDAY_09.plusSeconds(600)),
                open(completed.id(), PhaseKind.REVIEW, MONDAY_15));

        ViewTaskDetailResult detail = service.execute(completed.id());

        assertThat(detail.completedAt()).isEqualTo(MONDAY_15);
        assertThat(detail.deadlineMet())
                .as("the second submission was late, and it is the one being judged")
                .contains(false);
    }

    @Test
    void workSubmittedExactlyOnTheDeadlineIsOnTime() {
        Task completed = completedWithDeadline(MONDAY_15);
        ionutOpens(completed);
        theProofIs(CompletionProof.of(completed.id(), "Done.", null, MONDAY_15));
        thePhasesAre(open(completed.id(), PhaseKind.REVIEW, MONDAY_15));

        assertThat(service.execute(completed.id()).deadlineMet()).contains(true);
    }
}
