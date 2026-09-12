package com.flowops.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.flowops.discovery.application.closebracket.CloseBracket;
import com.flowops.discovery.application.closebracket.CloseOutcome;
import com.flowops.discovery.application.closejob.CloseJob;
import com.flowops.discovery.application.shared.port.DiscoveryNoticePort;
import com.flowops.discovery.application.shared.port.PersonRolePort;
import com.flowops.discovery.application.shared.port.WorkBracketPort;
import com.flowops.discovery.application.shared.port.WorkGraphPort;
import com.flowops.discovery.domain.enums.WaitKind;
import com.flowops.discovery.domain.model.BracketAddress;
import com.flowops.discovery.domain.model.BracketId;
import com.flowops.discovery.domain.model.Job;
import com.flowops.discovery.domain.model.JobId;
import com.flowops.discovery.domain.model.WorkBracket;
import com.flowops.discovery.domain.model.WorkNodeId;
import com.flowops.discovery.domain.model.WorkNodeWait;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("DISCOVERY-CLOSE-BRACKET-01")
@Tag("DISCOVERY-CLOSE-JOB-01")
class TheNoticesThisZoneSendsTest {
    private static final Instant NOW = Instant.parse("2026-08-26T09:00:00Z");

    private final UUID maria = UUID.randomUUID();
    private final UUID andrei = UUID.randomUUID();
    private final UUID nour = UUID.randomUUID();
    private final UUID karim = UUID.randomUUID();

    private final JobId job = JobId.of(UUID.randomUUID());
    private final UUID conversation = UUID.randomUUID();

    private final Map<BracketId, WorkBracket> stored = new LinkedHashMap<>();
    private final List<WorkNodeWait> waits = new ArrayList<>();

    private final WorkBracketPort brackets = mock(WorkBracketPort.class);
    private final WorkGraphPort graph = mock(WorkGraphPort.class);
    private final DiscoveryNoticePort notices = mock(DiscoveryNoticePort.class);

    private CloseBracket closing;
    private CloseJob closingJobs;

    @BeforeEach
    void theGraphIsInMemoryAndTheNoticePortIsWatched() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        when(brackets.find(any())).thenAnswer(call -> Optional.ofNullable(stored.get(call.getArgument(0))));
        when(brackets.nextWaitId()).thenAnswer(call -> UUID.randomUUID());
        when(brackets.appendEndNode(any(), any(), any())).thenAnswer(call -> WorkNodeId.of(UUID.randomUUID()));
        when(brackets.openWaitsOn(any())).thenAnswer(call -> {
            BracketId target = call.getArgument(0);
            return waits.stream()
                    .filter(WorkNodeWait::isOpen)
                    .filter(wait -> wait.onBracketId().map(target::equals).orElse(false))
                    .toList();
        });
        when(brackets.openWaitsHeldBy(any())).thenAnswer(call -> {
            BracketId holder = call.getArgument(0);
            return waits.stream()
                    .filter(WorkNodeWait::isOpen)
                    .filter(wait -> wait.bracketId().equals(holder))
                    .toList();
        });
        org.mockito.Mockito.doAnswer(call -> {
                    WorkBracket saved = call.getArgument(0);
                    stored.put(saved.id(), saved);
                    return null;
                })
                .when(brackets)
                .save(any(WorkBracket.class));
        org.mockito.Mockito.doAnswer(call -> {
                    WorkNodeWait saved = call.getArgument(0);
                    if (waits.stream().noneMatch(held -> held.id().equals(saved.id()))) {
                        waits.add(saved);
                    }
                    return null;
                })
                .when(brackets)
                .save(any(WorkNodeWait.class));

        closing = new CloseBracket(brackets, graph, notices, clock);

        PersonRolePort people = mock(PersonRolePort.class);
        when(people.ownsTheWorkspace(any())).thenReturn(false);

        closingJobs = new CloseJob(brackets, closing, graph, notices, people, clock);
    }

    private WorkBracket bracketFor(UUID performer, String workType) {
        BracketAddress address = BracketAddress.internal(conversation, workType).performedBy(performer);
        WorkBracket bracket = WorkBracket.opened(
                BracketId.fresh(), job, address, WorkNodeId.of(UUID.randomUUID()), performer, NOW.minusSeconds(3600));
        stored.put(bracket.id(), bracket);
        return bracket;
    }

    private WorkBracket mariaWaitingOn(WorkBracket target) {
        WorkBracket hers = bracketFor(maria, "COPY");
        waits.add(WorkNodeWait.declared(
                UUID.randomUUID(),
                hers,
                WaitKind.COLLEAGUE,
                target,
                "need the video first",
                null,
                NOW.minusSeconds(60)));
        hers.waitingOnSomething(true);
        return hers;
    }

    @Test
    void threeHandoversInARowTellTheWaiterOnceAndOnlyAtTheRealDelivery() {
        WorkBracket nours = bracketFor(nour, "VIDEO");
        WorkBracket andreis = bracketFor(andrei, "VIDEO");
        WorkBracket karims = bracketFor(karim, "VIDEO");
        WorkBracket backToAndrei = bracketFor(andrei, "VIDEO");
        WorkBracket hers = mariaWaitingOn(nours);

        closing.handedOver(nours.id(), andreis);
        closing.handedOver(andreis.id(), karims);
        closing.handedOver(karims.id(), backToAndrei);

        verify(notices, never()).awaitedWorkArrived(any(), any());
        verify(notices, never()).awaitedWorkDied(any(), any());

        CloseOutcome delivered = closing.done(backToAndrei.id(), andrei);

        assertThat(delivered.released())
                .as("R14.8 - however long the chain, the wait is satisfied exactly once, at the delivery")
                .hasSize(1);
        verify(notices, times(1)).awaitedWorkArrived(eq(maria), eq(hers.id()));
        verify(notices, never()).awaitedWorkDied(any(), any());
        verifyNoMoreInteractions(notices);
    }

    @Test
    void aDroppedTargetWarnsTheWaiterAndNeverTellsHerItArrived() {
        WorkBracket andreis = bracketFor(andrei, "VIDEO");
        WorkBracket hers = mariaWaitingOn(andreis);

        CloseOutcome dropped = closing.dropped(andreis.id(), "the client cancelled the campaign", andrei);

        assertThat(dropped.released())
                .as("nothing was delivered, so nobody is released")
                .isEmpty();
        assertThat(dropped.bereaved()).hasSize(1);
        verify(notices, times(1)).awaitedWorkDied(eq(maria), eq(hers.id()));
        verify(notices, never()).awaitedWorkArrived(any(), any());
        verifyNoMoreInteractions(notices);
    }

    @Test
    void aCadenceCloseSatisfiesNothingAndWarnsTheWaiterInstead() {
        WorkBracket retainer = bracketFor(andrei, "SOCIAL");
        WorkBracket hers = mariaWaitingOn(retainer);

        CloseOutcome rolled = closing.closedByCadence(retainer.id());

        assertThat(rolled.released())
                .as("a window rolled over; nobody finished anything, so nobody is released")
                .isEmpty();
        verify(notices, never()).awaitedWorkArrived(any(), any());
        verify(notices, times(1)).awaitedWorkDied(eq(maria), eq(hers.id()));
        verifyNoMoreInteractions(notices);
    }

    @Test
    void clearingYourOwnWaitAnnouncesNothingToYou() {
        WorkBracket herOwnTarget = bracketFor(maria, "PHOTO");
        WorkBracket hers = mariaWaitingOn(herOwnTarget);

        CloseOutcome finished = closing.done(herOwnTarget.id(), maria);

        assertThat(finished.released())
                .as("the wait really is satisfied — what D7 withholds is the message, never the fact")
                .hasSize(1);
        assertThat(stored.get(hers.id()).state().isLive())
                .as("and she is unblocked, which is the thing the notice would only have repeated")
                .isTrue();
        verifyNoMoreInteractions(notices);
    }

    @Test
    void forcingAnEngagementClosedTellsEachHolderOfLiveWorkExactlyOnce() {
        Job aurora = Job.opened(job, "Rebranding Aurora Coffee", maria, NOW.minusSeconds(86400));
        WorkBracket karimsVideo = bracketFor(karim, "VIDEO");
        WorkBracket karimsEdit = bracketFor(karim, "EDIT");
        WorkBracket noursCopy = bracketFor(nour, "COPY");

        when(graph.findJob(job)).thenReturn(Optional.of(aurora));
        when(brackets.liveWorkIn(job)).thenReturn(List.of(karimsVideo, karimsEdit, noursCopy));
        when(brackets.boundaryOf(job)).thenReturn(Optional.empty());

        CloseJob.Ended ended = closingJobs.forceClose(job, maria, "the client pulled the budget");

        assertThat(ended.tellThem())
                .as("one person holding two live brackets is one person to tell")
                .containsExactly(karim, nour);
        verify(notices, times(1)).jobForceClosed(eq(karim), eq(job));
        verify(notices, times(1)).jobForceClosed(eq(nour), eq(job));
        verify(notices, never()).awaitedWorkDied(any(), any());
        verify(notices, never()).awaitedWorkArrived(any(), any());
        verifyNoMoreInteractions(notices);
    }
}
