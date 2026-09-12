package com.flowops.canvas.application.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.flowops.canvas.application.shared.port.InstanceScopePort;
import com.flowops.canvas.application.shared.port.SubscriberPermissionsPort;
import com.flowops.canvas.application.shared.port.TaskEventFeedPort;
import com.flowops.canvas.application.stream.exception.CursorTooOldException;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("CANVAS-VIEW-PROCESS-01")
class CanvasStreamTest {
    private static final UUID IOANA = UUID.randomUUID();
    private static final UUID DAN = UUID.randomUUID();
    private static final UUID RUN = UUID.randomUUID();
    private static final UUID ANOTHER_RUN = UUID.randomUUID();
    private static final Set<String> MAY_VIEW = Set.of("PROCESS_VIEW_OWN");

    private TaskEventFeedPort feed;
    private InstanceScopePort scope;
    private SubscriberPermissionsPort permissions;
    private CanvasStreamRegistry registry;
    private SubscribeToInstanceService subscribe;
    private PublishCanvasDeltasService publish;
    private RecordingSink ioana;

    @BeforeEach
    void setUp() {
        feed = mock(TaskEventFeedPort.class);
        scope = mock(InstanceScopePort.class);
        permissions = mock(SubscriberPermissionsPort.class);
        registry = new CanvasStreamRegistry();
        subscribe = new SubscribeToInstanceService(feed, scope, registry);
        publish = new PublishCanvasDeltasService(feed, scope, permissions, registry);
        ioana = new RecordingSink();

        when(feed.currentCursor()).thenReturn(10L);
        when(feed.after(anyLong(), anyInt())).thenReturn(List.of());
        when(scope.mayView(any(), any(), any())).thenReturn(false);
        when(scope.mayView(IOANA, MAY_VIEW, RUN)).thenReturn(true);

        when(permissions.heldBy(any())).thenReturn(Set.of());
        when(permissions.heldBy(IOANA)).thenReturn(MAY_VIEW);
    }

    @Test
    void aMoveReachesAnOpenStreamWatchingThatRun() {
        subscribe.subscribe(IOANA, MAY_VIEW, RUN, null, ioana);
        UUID moved = UUID.randomUUID();
        when(scope.instanceOf(moved)).thenReturn(Optional.of(RUN));
        when(feed.after(10L, PublishCanvasDeltasService.BATCH)).thenReturn(List.of(event(11, moved, "TASK_BLOCKED")));

        publish.pump();

        assertThat(ioana.received).hasSize(1);
        assertThat(ioana.received.get(0).kind()).isEqualTo("TASK_BLOCKED");
        assertThat(ioana.received.get(0).cursor())
                .as("the delta carries its own position, so a reconnect resumes exactly after it")
                .isEqualTo(11L);
    }

    @Test
    void aSecondPassDoesNotResendWhatTheFirstSent() {
        subscribe.subscribe(IOANA, MAY_VIEW, RUN, null, ioana);
        UUID moved = UUID.randomUUID();
        when(scope.instanceOf(moved)).thenReturn(Optional.of(RUN));
        when(feed.after(10L, PublishCanvasDeltasService.BATCH)).thenReturn(List.of(event(11, moved, "TASK_BLOCKED")));
        when(feed.after(11L, PublishCanvasDeltasService.BATCH)).thenReturn(List.of());

        publish.pump();
        publish.pump();

        assertThat(ioana.received).hasSize(1);
    }

    @Test
    void aStreamOnAnotherRunHearsNothingAboutThisOne() {
        when(scope.mayView(IOANA, MAY_VIEW, ANOTHER_RUN)).thenReturn(true);
        subscribe.subscribe(IOANA, MAY_VIEW, ANOTHER_RUN, null, ioana);
        UUID moved = UUID.randomUUID();
        when(scope.instanceOf(moved)).thenReturn(Optional.of(RUN));
        when(feed.after(10L, PublishCanvasDeltasService.BATCH)).thenReturn(List.of(event(11, moved, "TASK_BLOCKED")));

        publish.pump();

        assertThat(ioana.received).isEmpty();
    }

    @Test
    void aSubscriberWhoLosesScopeStopsReceivingWithoutTheStreamClosing() {
        subscribe.subscribe(IOANA, MAY_VIEW, RUN, null, ioana);
        UUID moved = UUID.randomUUID();
        when(scope.instanceOf(moved)).thenReturn(Optional.of(RUN));
        when(feed.after(10L, PublishCanvasDeltasService.BATCH)).thenReturn(List.of(event(11, moved, "TASK_BLOCKED")));
        publish.pump();

        when(scope.mayView(IOANA, MAY_VIEW, RUN)).thenReturn(false);
        when(feed.after(11L, PublishCanvasDeltasService.BATCH)).thenReturn(List.of(event(12, moved, "TASK_CLOSED")));
        publish.pump();

        assertThat(ioana.received).extracting(CanvasDelta::kind).containsExactly("TASK_BLOCKED");
        assertThat(registry.size()).as("still connected, simply told nothing").isEqualTo(1);
    }

    @Test
    void aSubscriberDemotedMidStreamStopsReceivingOnTheVeryNextPass() {
        subscribe.subscribe(IOANA, MAY_VIEW, RUN, null, ioana);
        UUID moved = UUID.randomUUID();
        when(scope.instanceOf(moved)).thenReturn(Optional.of(RUN));
        when(feed.after(10L, PublishCanvasDeltasService.BATCH)).thenReturn(List.of(event(11, moved, "TASK_BLOCKED")));
        publish.pump();

        when(permissions.heldBy(IOANA)).thenReturn(Set.of());
        when(feed.after(11L, PublishCanvasDeltasService.BATCH)).thenReturn(List.of(event(12, moved, "TASK_CLOSED")));
        publish.pump();

        assertThat(ioana.received)
                .as("a stream must not go on treating somebody as what they were when they connected")
                .extracting(CanvasDelta::kind)
                .containsExactly("TASK_BLOCKED");
    }

    @Test
    void aTaskBelongingToNoRunReachesNobody() {
        subscribe.subscribe(IOANA, MAY_VIEW, RUN, null, ioana);
        UUID byHand = UUID.randomUUID();
        when(scope.instanceOf(byHand)).thenReturn(Optional.empty());
        when(feed.after(10L, PublishCanvasDeltasService.BATCH)).thenReturn(List.of(event(11, byHand, "TASK_CREATED")));

        publish.pump();

        assertThat(ioana.received).isEmpty();
    }

    @Test
    void aCursorReplaysTheGapOldestFirst() {
        UUID moved = UUID.randomUUID();
        when(scope.instanceOf(moved)).thenReturn(Optional.of(RUN));
        when(feed.after(8L, SubscribeToInstanceService.REPLAY_LIMIT))
                .thenReturn(List.of(event(9, moved, "TASK_STARTED"), event(10, moved, "TASK_BLOCKED")));

        subscribe.subscribe(IOANA, MAY_VIEW, RUN, 8L, ioana);

        assertThat(ioana.received).extracting(CanvasDelta::kind).containsExactly("TASK_STARTED", "TASK_BLOCKED");
    }

    @Test
    void aReplayIsRefusedEventByEventAgainstTheScopeOfNow() {
        UUID moved = UUID.randomUUID();
        when(scope.instanceOf(moved)).thenReturn(Optional.of(RUN));
        when(scope.mayView(DAN, MAY_VIEW, RUN)).thenReturn(true, false);
        when(feed.after(8L, SubscribeToInstanceService.REPLAY_LIMIT))
                .thenReturn(List.of(event(9, moved, "TASK_STARTED")));

        subscribe.subscribe(DAN, MAY_VIEW, RUN, 8L, ioana);

        assertThat(ioana.received)
                .as("admitted at connect, refused for the event itself")
                .isEmpty();
    }

    @Test
    void aGapLargerThanTheReplayBoundIsRefusedRatherThanStreamed() {
        when(feed.currentCursor()).thenReturn(10_000L);

        assertThatThrownBy(() -> subscribe.subscribe(IOANA, MAY_VIEW, RUN, 1L, ioana))
                .isInstanceOf(CursorTooOldException.class);
        assertThat(registry.size())
                .as("nothing is registered for a subscription that was refused")
                .isZero();
    }

    @Test
    void aRunTheCallerMayNotSeeRefusesWithoutSayingWhy() {
        assertThatThrownBy(() -> subscribe.subscribe(DAN, MAY_VIEW, RUN, null, ioana))
                .isInstanceOf(StreamNotAvailableException.class);
        assertThat(registry.size()).isZero();
    }

    @Test
    void aStreamThatCannotBeWrittenToIsDroppedAndClosed() {
        RecordingSink broken = new RecordingSink() {
            @Override
            public void send(CanvasDelta delta) {
                throw new IllegalStateException("the client hung up");
            }
        };
        subscribe.subscribe(IOANA, MAY_VIEW, RUN, null, broken);
        UUID moved = UUID.randomUUID();
        when(scope.instanceOf(moved)).thenReturn(Optional.of(RUN));
        when(feed.after(10L, PublishCanvasDeltasService.BATCH)).thenReturn(List.of(event(11, moved, "TASK_BLOCKED")));

        publish.pump();

        assertThat(registry.size()).isZero();
        assertThat(broken.closed)
                .as("forgetting a subscription without closing its emitter leaves the client on a "
                        + "socket that will never speak again, for the whole thirty-minute hold")
                .isTrue();
    }

    @Test
    void theWatermarkMovesEvenWhileNobodyIsWatching() {
        when(feed.currentCursor()).thenReturn(42L);

        publish.pump();

        assertThat(registry.watermark(-1L)).isEqualTo(42L);
    }

    @Test
    void theCursorOfferedToASnapshotIsTheLogsOwnPositionAndNotThePumps() {
        when(feed.currentCursor()).thenReturn(77L);
        registry.pumpedTo(40L);

        assertThat(subscribe.currentCursor()).isEqualTo(77L);
    }

    @Test
    void anEventBetweenTheCursorAndTheSubscriptionIsReplayedRatherThanLost() {
        when(feed.currentCursor()).thenReturn(50L);
        long cursor = subscribe.currentCursor();

        UUID moved = UUID.randomUUID();
        when(scope.instanceOf(moved)).thenReturn(Optional.of(RUN));
        when(feed.currentCursor()).thenReturn(51L);
        when(feed.after(cursor, SubscribeToInstanceService.REPLAY_LIMIT))
                .thenReturn(List.of(event(51, moved, "TASK_BLOCKED")));

        subscribe.subscribe(IOANA, MAY_VIEW, RUN, cursor, ioana);

        assertThat(ioana.received).hasSize(1);
        assertThat(ioana.received.get(0).cursor()).isEqualTo(51L);
    }

    @Test
    void theSubscriptionIsRegisteredBeforeItsGapIsReplayed() {
        when(feed.currentCursor()).thenReturn(50L);
        UUID moved = UUID.randomUUID();
        when(scope.instanceOf(moved)).thenReturn(Optional.of(RUN));

        when(feed.after(40L, SubscribeToInstanceService.REPLAY_LIMIT)).thenAnswer(reading -> {
            assertThat(registry.size())
                    .as("an event landing while the gap is being read must already have somewhere to go")
                    .isEqualTo(1);
            return List.of(event(41, moved, "TASK_BLOCKED"));
        });

        subscribe.subscribe(IOANA, MAY_VIEW, RUN, 40L, ioana);

        assertThat(ioana.received).hasSize(1);
    }

    @Test
    void aReplayThatFailsLeavesNoSubscriptionBehind() {
        when(feed.currentCursor()).thenReturn(50L);
        when(feed.after(40L, SubscribeToInstanceService.REPLAY_LIMIT))
                .thenThrow(new IllegalStateException("the connection pool is exhausted"));

        assertThatThrownBy(() -> subscribe.subscribe(IOANA, MAY_VIEW, RUN, 40L, ioana))
                .isInstanceOf(IllegalStateException.class);

        assertThat(registry.size())
                .as("an emitter the endpoint never returned can never be written to and so can never "
                        + "be dropped — it has to be removed here or it is permanent")
                .isZero();
    }

    @Test
    void aSubscriberArrivingWhileThePumpDecidesNobodyIsWatchingIsStillServed() {
        registry.pumpedTo(10L);
        UUID moved = UUID.randomUUID();
        when(scope.instanceOf(moved)).thenReturn(Optional.of(RUN));
        when(feed.after(10L, PublishCanvasDeltasService.BATCH)).thenReturn(List.of(event(11, moved, "TASK_BLOCKED")));

        when(feed.currentCursor()).thenAnswer(reading -> {
            if (registry.size() == 0) {
                registry.add(new CanvasSubscription(UUID.randomUUID(), IOANA, RUN, ioana));
            }
            return 42L;
        });

        publish.pump();

        assertThat(ioana.received)
                .as("the watermark must not jump past an event while somebody is arriving to watch for it")
                .hasSize(1);
    }

    private TaskEventFeedPort.Event event(long sequence, UUID task, String kind) {
        return new TaskEventFeedPort.Event(sequence, task, kind, Instant.parse("2026-08-15T09:00:00Z"));
    }

    private static class RecordingSink implements CanvasSink {
        private final List<CanvasDelta> received = new ArrayList<>();

        private int beats;
        private boolean gone;
        private boolean closed;

        @Override
        public void send(CanvasDelta delta) {
            received.add(delta);
        }

        @Override
        public void keepAlive() throws Exception {
            if (gone) {
                throw new IOException("the reader behind this stream has gone");
            }
            beats += 1;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    @Nested
    class TheHeartbeat {
        @Test
        void beatsOnEveryOpenStream() {
            subscribe.subscribe(IOANA, MAY_VIEW, RUN, null, ioana);

            new CanvasHeartbeat(registry).beat();
            new CanvasHeartbeat(registry).beat();

            assertThat(ioana.beats).isEqualTo(2);
        }

        @Test
        void dropsAStreamItCannotWriteTo() {
            subscribe.subscribe(IOANA, MAY_VIEW, RUN, null, ioana);
            assertThat(registry.size()).isEqualTo(1);

            ioana.gone = true;
            new CanvasHeartbeat(registry).beat();

            assertThat(registry.size()).isZero();
        }

        @Test
        void closesTheStreamItDropsRatherThanLeavingItOpenAndSilent() {
            subscribe.subscribe(IOANA, MAY_VIEW, RUN, null, ioana);

            ioana.gone = true;
            new CanvasHeartbeat(registry).beat();

            assertThat(ioana.closed)
                    .as("a dropped subscription whose connection stays open goes silent on a healthy server")
                    .isTrue();
        }

        @Test
        void movesNobodysCursor() {
            subscribe.subscribe(IOANA, MAY_VIEW, RUN, null, ioana);
            long before = registry.watermark(-1L);

            new CanvasHeartbeat(registry).beat();

            assertThat(registry.watermark(-1L)).isEqualTo(before);
            assertThat(ioana.received).isEmpty();
        }
    }
}
