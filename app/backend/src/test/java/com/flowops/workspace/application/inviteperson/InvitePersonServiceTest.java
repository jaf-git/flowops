package com.flowops.workspace.application.inviteperson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowops.workspace.application.shared.exception.AlreadyMemberException;
import com.flowops.workspace.application.shared.exception.DeactivatedMemberException;
import com.flowops.workspace.application.shared.exception.DeclineWindowException;
import com.flowops.workspace.application.shared.exception.DuplicateInvitationException;
import com.flowops.workspace.application.shared.exception.InvitationLimitReachedException;
import com.flowops.workspace.application.shared.exception.InvitationRefusedException;
import com.flowops.workspace.application.shared.exception.ManagerInactiveException;
import com.flowops.workspace.application.shared.exception.SelfInvitationException;
import com.flowops.workspace.application.shared.exception.SetupNotPermittedException;
import com.flowops.workspace.application.shared.port.AppendWorkspaceEventPort;
import com.flowops.workspace.application.shared.port.DescribePeoplePort;
import com.flowops.workspace.application.shared.port.GenerateInvitationTokenPort;
import com.flowops.workspace.application.shared.port.IdentifyCallerPort;
import com.flowops.workspace.application.shared.port.InvitationLimitPort;
import com.flowops.workspace.application.shared.port.LoadInvitationPort;
import com.flowops.workspace.application.shared.port.LoadMembershipPort;
import com.flowops.workspace.application.shared.port.LoadWorkspacePort;
import com.flowops.workspace.application.shared.port.SaveInvitationPort;
import com.flowops.workspace.application.shared.port.SendInvitationPort;
import com.flowops.workspace.domain.enums.InvitationState;
import com.flowops.workspace.domain.enums.InvitedRole;
import com.flowops.workspace.domain.enums.MembershipStatus;
import com.flowops.workspace.domain.enums.WorkspaceAction;
import com.flowops.workspace.domain.event.WorkspaceEvent;
import com.flowops.workspace.domain.model.EmailAddress;
import com.flowops.workspace.domain.model.Invitation;
import com.flowops.workspace.domain.model.InvitationId;
import com.flowops.workspace.domain.model.InvitationToken;
import com.flowops.workspace.domain.model.Membership;
import com.flowops.workspace.domain.model.MembershipId;
import com.flowops.workspace.domain.model.PersonId;
import com.flowops.workspace.domain.model.Workspace;
import com.flowops.workspace.domain.model.WorkspaceId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("WORKSPACE-INVITE-01")
@ExtendWith(MockitoExtension.class)
class InvitePersonServiceTest {
    private static final WorkspaceId WORKSPACE = WorkspaceId.of(UUID.randomUUID());
    private static final PersonId MARIA = PersonId.of(UUID.randomUUID());
    private static final MembershipId MARIA_MEMBERSHIP = MembershipId.of(UUID.randomUUID());
    private static final EmailAddress IONUT = EmailAddress.of("ionut@atelier.ro");
    private static final Instant NOW = Instant.parse("2026-08-04T09:00:00Z");

    private static final Duration LIFETIME = Duration.ofDays(7);
    private static final Duration RECONSIDERATION = Duration.ofDays(30);

    @Mock
    private IdentifyCallerPort identifyCallerPort;

    @Mock
    private DescribePeoplePort describePeoplePort;

    @Mock
    private LoadWorkspacePort loadWorkspacePort;

    @Mock
    private LoadMembershipPort loadMembershipPort;

    @Mock
    private LoadInvitationPort loadInvitationPort;

    @Mock
    private InvitationLimitPort invitationLimitPort;

    @Mock
    private GenerateInvitationTokenPort generateInvitationTokenPort;

    @Mock
    private SaveInvitationPort saveInvitationPort;

    @Mock
    private AppendWorkspaceEventPort appendWorkspaceEventPort;

    @Mock
    private SendInvitationPort sendInvitationPort;

    private InvitePersonService service;

    @BeforeEach
    void buildTheService() {
        service = new InvitePersonService(
                identifyCallerPort,
                describePeoplePort,
                loadWorkspacePort,
                loadMembershipPort,
                loadInvitationPort,
                invitationLimitPort,
                generateInvitationTokenPort,
                saveInvitationPort,
                appendWorkspaceEventPort,
                sendInvitationPort,
                LIFETIME,
                RECONSIDERATION,
                Clock.fixed(NOW, ZoneOffset.UTC));

        lenient()
                .when(identifyCallerPort.currentCaller())
                .thenReturn(Optional.of(new IdentifyCallerPort.Caller(MARIA, true, true, "WORKSPACE")));
        lenient().when(loadWorkspacePort.load()).thenReturn(Workspace.rebuild(WORKSPACE, null, null, NOW));
        lenient().when(loadMembershipPort.findByPerson(MARIA)).thenReturn(Optional.of(owner()));
        lenient().when(loadMembershipPort.findById(MARIA_MEMBERSHIP)).thenReturn(Optional.of(owner()));
        lenient().when(loadMembershipPort.findByEmail(IONUT)).thenReturn(Optional.empty());
        lenient().when(loadInvitationPort.findOpenFor(IONUT)).thenReturn(Optional.empty());
        lenient().when(loadInvitationPort.findLastDeclineFor(IONUT)).thenReturn(Optional.empty());
        lenient()
                .when(generateInvitationTokenPort.mint())
                .thenReturn(
                        new GenerateInvitationTokenPort.MintedToken(InvitationToken.of("clear-token"), "hashed-token"));
        lenient().when(saveInvitationPort.save(any())).thenAnswer(call -> call.getArgument(0));

        lenient()
                .when(describePeoplePort.describe(any()))
                .thenReturn(List.of(new DescribePeoplePort.PersonDescription(MARIA, "Maria Ionescu", "OWNER")));
    }

    @Test
    void namingAnEmployeeAsTheIntendedManagerIsRefused() {
        when(describePeoplePort.describe(any()))
                .thenReturn(List.of(new DescribePeoplePort.PersonDescription(MARIA, "Maria Ionescu", "EMPLOYEE")));

        assertThatThrownBy(() -> service.execute(invitingIonut()))
                .isInstanceOf(InvitationRefusedException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(InvitationRefusedException.class))
                .extracting(InvitationRefusedException::code)
                .isEqualTo("MANAGER_NOT_ELIGIBLE");

        verify(saveInvitationPort, never()).save(any());
    }

    private static Membership owner() {
        return new Membership(MARIA_MEMBERSHIP, MARIA, MembershipStatus.ACTIVE, null);
    }

    private static Membership someoneElse(MembershipStatus status) {
        return new Membership(
                MembershipId.of(UUID.randomUUID()), PersonId.of(UUID.randomUUID()), status, MARIA_MEMBERSHIP);
    }

    private InvitePersonCommand invitingIonut() {
        return new InvitePersonCommand(IONUT, InvitedRole.EMPLOYEE, MARIA_MEMBERSHIP);
    }

    @Test
    void theOwnerInvitesAFreeAddressAndTheInvitationIsCreatedWithATokenAndAnExpiry() {
        InvitePersonResult result = service.execute(invitingIonut());

        assertThat(result.email()).isEqualTo(IONUT);
        assertThat(result.intendedRole()).isEqualTo(InvitedRole.EMPLOYEE);
        assertThat(result.state()).isEqualTo(InvitationState.SENT);
        assertThat(result.expiresAt()).isEqualTo(NOW.plus(LIFETIME));

        ArgumentCaptor<Invitation> saved = ArgumentCaptor.forClass(Invitation.class);
        verify(saveInvitationPort).save(saved.capture());
        assertThat(saved.getValue().tokenHash()).isEqualTo("hashed-token");
        assertThat(saved.getValue().email()).isEqualTo(IONUT);

        verify(sendInvitationPort).sendInvitation(IONUT, InvitationToken.of("clear-token"));
    }

    @Test
    void theTokenIsNeverReturnedToTheCaller() {
        InvitePersonResult result = service.execute(invitingIonut());

        assertThat(result.toString()).doesNotContain("clear-token");
        assertThat(InvitationToken.of("clear-token").toString()).doesNotContain("clear-token");
    }

    @Test
    void anInvitationCreatedEventIsAppendedNamingTheActorAndTheMoment() {
        service.execute(invitingIonut());

        ArgumentCaptor<WorkspaceEvent> event = ArgumentCaptor.forClass(WorkspaceEvent.class);
        verify(appendWorkspaceEventPort).append(event.capture());
        assertThat(event.getValue().action()).isEqualTo(WorkspaceAction.INVITATION_CREATED);
        assertThat(event.getValue().actor()).isEqualTo(MARIA);
        assertThat(event.getValue().occurredAt()).isEqualTo(NOW);
        assertThat(event.getValue().toString()).doesNotContain(IONUT.value());
    }

    @Test
    void anAddressWithAnOpenInvitationGetsNoSecondOne() {
        when(loadInvitationPort.findOpenFor(IONUT)).thenReturn(Optional.of(openInvitation()));

        assertRefusedWith(DuplicateInvitationException.class, "DUPLICATE_INVITATION");
        verify(saveInvitationPort, never()).save(any());
        verifyNoInteractions(sendInvitationPort);
    }

    @Test
    void anAddressBelongingToADeactivatedMemberIsRefusedAsDeactivatedNotAsDuplicate() {
        when(loadMembershipPort.findByEmail(IONUT)).thenReturn(Optional.of(someoneElse(MembershipStatus.DEACTIVATED)));

        assertRefusedWith(DeactivatedMemberException.class, "DEACTIVATED_MEMBER");
        verify(saveInvitationPort, never()).save(any());
    }

    @Test
    void anAddressBelongingToAnActiveMemberIsRefusedAsAlreadyHere() {
        when(loadMembershipPort.findByEmail(IONUT)).thenReturn(Optional.of(someoneElse(MembershipStatus.ACTIVE)));

        assertRefusedWith(AlreadyMemberException.class, "ALREADY_MEMBER");
        verify(saveInvitationPort, never()).save(any());
    }

    @Test
    void theInviterNamingTheirOwnAddressIsToldTheyAreAlreadyHere() {
        when(loadMembershipPort.findByEmail(IONUT)).thenReturn(Optional.of(owner()));

        assertRefusedWith(SelfInvitationException.class, "SELF_INVITATION");
        verify(saveInvitationPort, never()).save(any());
        verifyNoInteractions(sendInvitationPort);
    }

    @Test
    void anAddressThatDeclinedInsideTheReconsiderationWindowIsRefused() {
        when(loadInvitationPort.findLastDeclineFor(IONUT))
                .thenReturn(Optional.of(NOW.minus(RECONSIDERATION).plusSeconds(60)));

        assertRefusedWith(DeclineWindowException.class, "DECLINE_WINDOW");
        verify(saveInvitationPort, never()).save(any());
        verifyNoInteractions(sendInvitationPort);
    }

    @Test
    void anAddressThatDeclinedBeforeTheWindowClosedMayBeInvitedAgain() {
        when(loadInvitationPort.findLastDeclineFor(IONUT))
                .thenReturn(Optional.of(NOW.minus(RECONSIDERATION).minusSeconds(60)));

        InvitePersonResult result = service.execute(invitingIonut());

        assertThat(result.state()).isEqualTo(InvitationState.SENT);
    }

    @Test
    void reachingTheInvitationLimitRefusesAndSendsNothing() {
        doThrow(new InvitationLimitReachedException("try again tomorrow"))
                .when(invitationLimitPort)
                .check(MARIA_MEMBERSHIP, IONUT);

        assertRefusedWith(InvitationLimitReachedException.class, "RATE_LIMIT");
        verify(saveInvitationPort, never()).save(any());
        verifyNoInteractions(sendInvitationPort);
    }

    @Test
    void anIntendedManagerDeactivatedAtSubmissionIsRefused() {
        when(loadMembershipPort.findById(MARIA_MEMBERSHIP))
                .thenReturn(Optional.of(new Membership(MARIA_MEMBERSHIP, MARIA, MembershipStatus.DEACTIVATED, null)));

        assertRefusedWith(ManagerInactiveException.class, "MANAGER_INACTIVE");
        verify(saveInvitationPort, never()).save(any());
    }

    private Invitation openInvitation() {
        return new Invitation(
                InvitationId.of(UUID.randomUUID()),
                WORKSPACE,
                IONUT,
                InvitedRole.EMPLOYEE,
                MARIA_MEMBERSHIP,
                MARIA_MEMBERSHIP,
                InvitationState.SENT,
                "hashed-token",
                NOW.plus(LIFETIME),
                NOW,
                null);
    }

    @Test
    void aDeactivatedInviterCannotInviteAnybody() {
        when(loadMembershipPort.findByPerson(MARIA))
                .thenReturn(Optional.of(new Membership(MARIA_MEMBERSHIP, MARIA, MembershipStatus.DEACTIVATED, null)));

        assertThatThrownBy(() -> service.execute(invitingIonut())).isInstanceOf(SetupNotPermittedException.class);

        verify(saveInvitationPort, never()).save(any());
        verifyNoInteractions(sendInvitationPort);
    }

    private void assertRefusedWith(Class<? extends InvitationRefusedException> type, String code) {
        assertThatThrownBy(() -> service.execute(invitingIonut()))
                .isInstanceOf(type)
                .asInstanceOf(InstanceOfAssertFactories.type(InvitationRefusedException.class))
                .extracting(InvitationRefusedException::code)
                .isEqualTo(code);
    }
}
