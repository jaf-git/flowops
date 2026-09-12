package com.flowops.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowops.notification.application.published.NoticeRequest;
import com.flowops.notification.application.raise.NotifyService;
import com.flowops.notification.application.release.ReleaseService;
import com.flowops.notification.application.shared.DuplicateGate;
import com.flowops.notification.application.shared.PreferenceGate;
import com.flowops.notification.application.shared.QuietHoursDecision;
import com.flowops.notification.application.shared.SubjectStateRegistry;
import com.flowops.notification.application.shared.port.NotificationStorePort;
import com.flowops.notification.application.shared.port.PreferencePort;
import com.flowops.notification.application.shared.port.RecipientStatePort;
import com.flowops.notification.application.shared.port.SubjectStatePort;
import com.flowops.notification.application.shared.port.WorkspaceCalendarPort;
import com.flowops.notification.domain.CancelReason;
import com.flowops.notification.domain.Notification;
import com.flowops.notification.domain.NotificationState;
import com.flowops.notification.domain.QuietHours;
import com.flowops.shared.notice.CancelCondition;
import com.flowops.shared.notice.NotificationGroup;
import com.flowops.shared.notice.NotificationKind;
import com.flowops.shared.notice.SubjectKind;
import com.flowops.shared.notice.SubjectRef;
import com.flowops.shared.time.WorkingCalendar;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TheThreeThingsDeliveryMustGetRightTest {
    private static final ZoneId BUCHAREST = ZoneId.of("Europe/Bucharest");
    private static final UUID SARA = UUID.randomUUID();
    private static final UUID A_TASK = UUID.randomUUID();

    private static final Instant FRIDAY_EVENING =
            LocalDateTime.parse("2026-08-21T19:20").atZone(BUCHAREST).toInstant();

    private static final Instant MONDAY_MORNING =
            LocalDateTime.parse("2026-08-24T09:00").atZone(BUCHAREST).toInstant();

    private final NotificationStorePort store = mock(NotificationStorePort.class);
    private final PreferencePort preferences = mock(PreferencePort.class);
    private final WorkspaceCalendarPort calendar = mock(WorkspaceCalendarPort.class);
    private final SubjectStatePort taskState = mock(SubjectStatePort.class);

    private void everythingEnabled() {
        when(preferences.of(any())).thenReturn(Map.of());
    }

    private NotifyService raiseAt(Instant now) {
        when(store.pendingAlreadyExists(any(), any(), any())).thenReturn(false);
        when(calendar.quietHours()).thenReturn(new QuietHours(LocalTime.of(22, 0), LocalTime.of(6, 0), BUCHAREST));
        when(calendar.workingCalendar())
                .thenReturn(WorkingCalendar.fromMask("YYYYYNN", LocalTime.of(9, 0), LocalTime.of(17, 0), BUCHAREST));

        return new NotifyService(
                new PreferenceGate(preferences),
                new DuplicateGate(store),
                new QuietHoursDecision(calendar),
                store,
                Clock.fixed(now, BUCHAREST));
    }

    private RecipientStatePort everybodyIsStillHere() {
        return userIds -> userIds;
    }

    private SubjectStateRegistry registryWhereTaskIs(boolean stillRelevant) {
        when(taskState.answersFor()).thenReturn(SubjectKind.TASK);
        when(taskState.stillRelevant(any(), any())).thenReturn(stillRelevant);
        when(taskState.stillExists(any())).thenReturn(true);

        List<SubjectStatePort> everyKind = new ArrayList<>(List.of(taskState));
        for (SubjectKind kind : SubjectKind.values()) {
            if (kind != SubjectKind.TASK) {
                SubjectStatePort other = mock(SubjectStatePort.class);
                when(other.answersFor()).thenReturn(kind);
                when(other.stillRelevant(any(), any())).thenReturn(stillRelevant);
                when(other.stillExists(any())).thenReturn(true);
                everyKind.add(other);
            }
        }
        return new SubjectStateRegistry(everyKind);
    }

    @Test
    @DisplayName("Sara is assigned work at 19:20 on Friday, does it on Saturday, and is not told on Monday")
    void theRecheckIsWhyThisFeatureIsWorthBuilding() {
        everythingEnabled();

        raiseAt(FRIDAY_EVENING).raise(new NoticeRequest(NotificationKind.WORK_ASSIGNED, SARA, SubjectRef.task(A_TASK)));

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(store).save(saved.capture());
        assertThat(saved.getValue().state())
                .as("raised inside quiet hours, so it waits")
                .isEqualTo(NotificationState.HELD);
        assertThat(saved.getValue().deliverAfter())
                .as("and it waits for the next WORKING instant -- Monday at nine, not Saturday at six")
                .isEqualTo(MONDAY_MORNING);

        when(store.dueForRelease(any())).thenReturn(List.of(saved.getValue()));
        ReleaseService release = new ReleaseService(
                store, registryWhereTaskIs(false), everybodyIsStillHere(), Clock.fixed(MONDAY_MORNING, BUCHAREST));

        assertThat(release.release()).as("nothing is delivered").isZero();

        ArgumentCaptor<Notification> settled = ArgumentCaptor.forClass(Notification.class);
        verify(store).update(settled.capture());
        assertThat(settled.getValue().state()).isEqualTo(NotificationState.CANCELLED);
        assertThat(settled.getValue().cancelReason())
                .as("kept with its reason -- a queue that silently loses rows cannot be debugged")
                .isEqualTo(CancelReason.SUBJECT_ALREADY_ACTED_ON);
    }

    @Test
    @DisplayName("I13 — a notice raised before somebody left is cancelled rather than delivered after")
    void nothingIsDeliveredToSomebodyWhoHasLeft() {
        UUID nour = UUID.randomUUID();

        Notification hers = Notification.held(
                UUID.randomUUID(),
                nour,
                NotificationKind.STEP_STALLED,
                SubjectRef.step(A_TASK),
                FRIDAY_EVENING,
                MONDAY_MORNING);
        Notification saras = Notification.held(
                UUID.randomUUID(),
                SARA,
                NotificationKind.STEP_STALLED,
                SubjectRef.step(A_TASK),
                FRIDAY_EVENING,
                MONDAY_MORNING);

        when(store.dueForRelease(any())).thenReturn(List.of(hers, saras));

        ReleaseService release = new ReleaseService(
                store,
                registryWhereTaskIs(true),
                userIds -> userIds.stream().filter(id -> !id.equals(nour)).collect(Collectors.toSet()),
                Clock.fixed(MONDAY_MORNING, BUCHAREST));

        assertThat(release.release())
                .as("Sara is still here, so her notice is still delivered -- the pass has not simply stopped")
                .isEqualTo(1);

        ArgumentCaptor<Notification> settled = ArgumentCaptor.forClass(Notification.class);
        verify(store, times(2)).update(settled.capture());

        Notification afterHers = settled.getAllValues().stream()
                .filter(one -> one.recipient().equals(nour))
                .findFirst()
                .orElseThrow();

        assertThat(afterHers.state()).as("I13 - it does not reach her").isEqualTo(NotificationState.CANCELLED);
        assertThat(afterHers.cancelReason())
                .as("and it says why, because the count of these means people are leaving faster than "
                        + "their work is handed on")
                .isEqualTo(CancelReason.RECIPIENT_HAS_LEFT);
    }

    @Test
    @DisplayName("the release pass run twice delivers once, counted rather than inspected")
    void runningItTwiceDeliversOnce() {
        Notification held = Notification.held(
                UUID.randomUUID(),
                SARA,
                NotificationKind.STEP_STALLED,
                SubjectRef.step(A_TASK),
                FRIDAY_EVENING,
                MONDAY_MORNING);

        SubjectStateRegistry registry = registryWhereTaskIs(true);
        when(taskState.answersFor()).thenReturn(SubjectKind.TASK);
        ReleaseService release =
                new ReleaseService(store, registry, everybodyIsStillHere(), Clock.fixed(MONDAY_MORNING, BUCHAREST));

        when(store.dueForRelease(any())).thenReturn(List.of(held)).thenReturn(List.of());

        assertThat(release.release()).isEqualTo(1);
        assertThat(release.release()).isZero();
    }

    @Test
    @DisplayName("an escalation raised at 02:00 is delivered, because a last resort a setting can silence is not one")
    void escalationsIgnoreQuietHoursAndPreferences() {
        Instant twoInTheMorning =
                LocalDateTime.parse("2026-08-25T02:00").atZone(BUCHAREST).toInstant();

        when(preferences.of(any()))
                .thenReturn(Map.of(
                        NotificationGroup.ASSIGNMENT, false,
                        NotificationGroup.TIME, false,
                        NotificationGroup.PROCESS, false,
                        NotificationGroup.WEEKLY, false,
                        NotificationGroup.ESCALATION, false));

        raiseAt(twoInTheMorning)
                .raise(new NoticeRequest(NotificationKind.OVERDUE_RUNG_1, SARA, SubjectRef.task(A_TASK)));

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(store).save(saved.capture());
        assertThat(saved.getValue().state())
                .as("delivered at two in the morning, not held until nine")
                .isEqualTo(NotificationState.DELIVERED);
    }

    @Test
    @DisplayName("a disabled group creates no row at all, rather than a suppressed one")
    void aDisabledGroupIsNeverCreated() {
        when(preferences.of(any())).thenReturn(Map.of(NotificationGroup.ASSIGNMENT, false));

        raiseAt(MONDAY_MORNING).raise(new NoticeRequest(NotificationKind.WORK_ASSIGNED, SARA, SubjectRef.task(A_TASK)));

        verify(store, never()).save(any());
    }

    @Test
    @DisplayName("a second notice about the same thing is dropped while the first is unread")
    void deduplication() {
        everythingEnabled();
        NotifyService notify = raiseAt(MONDAY_MORNING);
        when(store.pendingAlreadyExists(eq(SARA), eq(NotificationKind.WORK_ASSIGNED), any()))
                .thenReturn(true);

        notify.raise(new NoticeRequest(NotificationKind.WORK_ASSIGNED, SARA, SubjectRef.task(A_TASK)));

        verify(store, never()).save(any());
    }

    @Test
    @DisplayName("a notice whose kind and subject disagree is refused before it reaches the store")
    void aNoticeIsAboutTheKindOfThingItsKindSays() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new NoticeRequest(NotificationKind.WORK_ASSIGNED, SARA, SubjectRef.step(A_TASK)));
    }

    @Test
    @DisplayName("every cancel condition in the catalogue has a subject kind that answers for it")
    void theCatalogueIsAnswerable() {
        List<SubjectStatePort> missingTask = new ArrayList<>();
        for (SubjectKind kind : SubjectKind.values()) {
            if (kind != SubjectKind.TASK) {
                SubjectStatePort port = mock(SubjectStatePort.class);
                when(port.answersFor()).thenReturn(kind);
                missingTask.add(port);
            }
        }
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, () -> new SubjectStateRegistry(missingTask));

        for (NotificationKind kind : NotificationKind.values()) {
            assertThat(kind.cancelCondition())
                    .as(kind + " must answer when it is cancelled")
                    .isNotNull();
            assertThat(kind.subjectKind())
                    .as(kind + " must say what it is about")
                    .isNotNull();
            assertThat(CancelCondition.valueOf(kind.cancelCondition().name())).isNotNull();
        }
    }
}
