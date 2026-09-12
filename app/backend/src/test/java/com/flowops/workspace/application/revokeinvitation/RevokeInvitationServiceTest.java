package com.flowops.workspace.application.revokeinvitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowops.workspace.application.shared.exception.InvitationAlreadyAcceptedException;
import com.flowops.workspace.application.shared.exception.InvitationNotFoundException;
import com.flowops.workspace.application.shared.exception.InvitationNotYoursException;
import com.flowops.workspace.application.shared.exception.InvitationRefusedException;
import com.flowops.workspace.application.shared.exception.NotAuthenticatedException;
import com.flowops.workspace.application.shared.port.AppendWorkspaceEventPort;
import com.flowops.workspace.application.shared.port.CallerPermissionsPort;
import com.flowops.workspace.application.shared.port.DescribePeoplePort;
import com.flowops.workspace.application.shared.port.IdentifyCallerPort;
import com.flowops.workspace.application.shared.port.LoadInvitationPort;
import com.flowops.workspace.application.shared.port.LoadMembershipPort;
import com.flowops.workspace.application.shared.port.LoadWorkspacePort;
import com.flowops.workspace.application.shared.port.SaveInvitationPort;
import com.flowops.workspace.domain.enums.InvitationState;
import com.flowops.workspace.domain.enums.InvitedRole;
import com.flowops.workspace.domain.enums.MembershipStatus;
import com.flowops.workspace.domain.enums.WorkspaceAction;
import com.flowops.workspace.domain.event.WorkspaceEvent;
import com.flowops.workspace.domain.model.EmailAddress;
import com.flowops.workspace.domain.model.Invitation;
import com.flowops.workspace.domain.model.InvitationId;
import com.flowops.workspace.domain.model.Membership;
import com.flowops.workspace.domain.model.MembershipId;
import com.flowops.workspace.domain.model.PersonId;
import com.flowops.workspace.domain.model.Workspace;
import com.flowops.workspace.domain.model.WorkspaceId;
import java.time.Clock;
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

@Tag("WORKSPACE-REVOKE-INVITE-01")
@ExtendWith(MockitoExtension.class)
class RevokeInvitationServiceTest {
    private static final WorkspaceId WORKSPACE = WorkspaceId.of(UUID.randomUUID());
    private static final PersonId MARIA_PERSON = PersonId.of(UUID.randomUUID());
    private static final PersonId IONUT_PERSON = PersonId.of(UUID.randomUUID());
    private static final MembershipId MARIA = MembershipId.of(UUID.randomUUID());
    private static final MembershipId IONUT = MembershipId.of(UUID.randomUUID());
    private static final InvitationId INVITATION = InvitationId.of(UUID.randomUUID());
    private static final Instant NOW = Instant.parse("2026-08-04T09:00:00Z");

    @Mock
    private IdentifyCallerPort identifyCallerPort;

    @Mock
    private LoadMembershipPort loadMembershipPort;

    @Mock
    private LoadInvitationPort loadInvitationPort;

    @Mock
    private SaveInvitationPort saveInvitationPort;

    @Mock
    private AppendWorkspaceEventPort appendWorkspaceEventPort;

    @Mock
    private LoadWorkspacePort loadWorkspacePort;

    @Mock
    private DescribePeoplePort describePeoplePort;

    @Mock
    private CallerPermissionsPort callerPermissionsPort;

    private RevokeInvitationService service;

    @BeforeEach
    void setUp() {
        service = new RevokeInvitationService(
                identifyCallerPort,
                loadMembershipPort,
                loadInvitationPort,
                saveInvitationPort,
                appendWorkspaceEventPort,
                loadWorkspacePort,
                describePeoplePort,
                callerPermissionsPort,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void theOwnerWithdrawsAnInvitationAndAnEventNamesWhoActed() {
        callerIsOwner();
        invitationIs(sentBy(MARIA));

        RevokeInvitationResult result = service.execute(new RevokeInvitationCommand(INVITATION));

        assertThat(result.state()).isEqualTo(InvitationState.REVOKED);
        assertThat(result.revokedAt()).isEqualTo(NOW);

        ArgumentCaptor<Invitation> saved = ArgumentCaptor.forClass(Invitation.class);
        verify(saveInvitationPort).save(saved.capture());
        assertThat(saved.getValue().state()).isEqualTo(InvitationState.REVOKED);

        ArgumentCaptor<WorkspaceEvent> event = ArgumentCaptor.forClass(WorkspaceEvent.class);
        verify(appendWorkspaceEventPort).append(event.capture());
        assertThat(event.getValue().action()).isEqualTo(WorkspaceAction.INVITATION_REVOKED);
        assertThat(event.getValue().actor()).isEqualTo(MARIA_PERSON);
    }

    @Test
    void aManagerWithdrawsTheInvitationTheySentThemselves() {
        callerIsManager();
        invitationIs(sentBy(IONUT));

        assertThat(service.execute(new RevokeInvitationCommand(INVITATION)).state())
                .isEqualTo(InvitationState.REVOKED);

        verify(saveInvitationPort).save(any());
    }

    @Test
    void aManagerReachingForSomebodyElsesInvitationIsRefusedAndItIsUnchanged() {
        callerIsManager();
        invitationIs(sentBy(MARIA));

        assertThatThrownBy(() -> service.execute(new RevokeInvitationCommand(INVITATION)))
                .isInstanceOf(InvitationNotYoursException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(InvitationRefusedException.class))
                .extracting(InvitationRefusedException::code)
                .isEqualTo("NOT_YOURS");

        verify(saveInvitationPort, never()).save(any());
        verify(appendWorkspaceEventPort, never()).append(any());
    }

    @Test
    void theOwnerWithdrawsAnInvitationSomebodyElseSent() {
        callerIsOwner();
        invitationIs(sentBy(IONUT));

        assertThat(service.execute(new RevokeInvitationCommand(INVITATION)).state())
                .isEqualTo(InvitationState.REVOKED);
    }

    @Test
    void anInvitationAlreadyRevokedAnswersSuccessAndChangesNothing() {
        callerIsOwner();
        invitationIs(sentBy(MARIA).revoke());

        RevokeInvitationResult result = service.execute(new RevokeInvitationCommand(INVITATION));

        assertThat(result.state()).isEqualTo(InvitationState.REVOKED);
        assertThat(result.revokedAt())
                .as("this call changed nothing, so it revoked nothing")
                .isNull();
        verify(saveInvitationPort, never()).save(any());
        verify(appendWorkspaceEventPort, never()).append(any());
    }

    @Test
    void anInvitationAlreadyDeclinedAnswersSuccessCarryingTheStateItIsAlreadyIn() {
        callerIsOwner();
        invitationIs(inState(InvitationState.DECLINED));

        RevokeInvitationResult result = service.execute(new RevokeInvitationCommand(INVITATION));

        assertThat(result.state()).isEqualTo(InvitationState.DECLINED);
        verify(appendWorkspaceEventPort, never()).append(any());
    }

    @Test
    void anInvitationInTheExpiredStateAnswersSuccessAndChangesNothing() {
        callerIsOwner();
        invitationIs(inState(InvitationState.EXPIRED));

        RevokeInvitationResult result = service.execute(new RevokeInvitationCommand(INVITATION));

        assertThat(result.state()).isEqualTo(InvitationState.EXPIRED);
        assertThat(result.revokedAt()).isNull();
        verify(saveInvitationPort, never()).save(any());
        verify(appendWorkspaceEventPort, never()).append(any());
    }

    @Test
    void anInvitationAwaitingApprovalIsWithdrawnAndBecomesTerminal() {
        callerIsOwner();
        invitationIs(inState(InvitationState.AWAITING_APPROVAL));

        assertThat(service.execute(new RevokeInvitationCommand(INVITATION)).state())
                .isEqualTo(InvitationState.REVOKED);
    }

    @Test
    void anInvitationSomebodyHasAcceptedIsRefusedAndNamesDeactivation() {
        callerIsOwner();
        invitationIs(inState(InvitationState.ACCEPTED));
        when(describePeoplePort.describe(any()))
                .thenReturn(
                        List.of(new DescribePeoplePort.PersonDescription(IONUT_PERSON, "Ionuț Petrescu", "EMPLOYEE")));
        when(loadMembershipPort.findByEmail(any()))
                .thenReturn(Optional.of(new Membership(IONUT, IONUT_PERSON, MembershipStatus.ACTIVE, MARIA)));

        assertThatThrownBy(() -> service.execute(new RevokeInvitationCommand(INVITATION)))
                .isInstanceOf(InvitationAlreadyAcceptedException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(InvitationAlreadyAcceptedException.class))
                .satisfies(refusal -> {
                    assertThat(refusal.memberName()).isEqualTo("Ionuț Petrescu");
                    assertThat(refusal.code()).isEqualTo("ALREADY_ACCEPTED");
                });

        verify(saveInvitationPort, never()).save(any());
        verify(appendWorkspaceEventPort, never()).append(any());
    }

    @Test
    void anIdentifierNamingNoInvitationIsRefusedRatherThanAnsweredWithSuccess() {
        callerIsOwner();
        when(loadInvitationPort.findByIdForUpdate(INVITATION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(new RevokeInvitationCommand(INVITATION)))
                .isInstanceOf(InvitationNotFoundException.class);

        verify(saveInvitationPort, never()).save(any());
    }

    @Test
    void aCallWithNoSessionBehindItIsRefused() {
        when(identifyCallerPort.currentCaller()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(new RevokeInvitationCommand(INVITATION)))
                .isInstanceOf(NotAuthenticatedException.class);

        verify(loadInvitationPort, never()).findByIdForUpdate(any());
    }

    @Test
    void theInvitationIsReadUnderALockRatherThanPlainly() {
        callerIsOwner();
        invitationIs(sentBy(MARIA));

        service.execute(new RevokeInvitationCommand(INVITATION));

        verify(loadInvitationPort).findByIdForUpdate(INVITATION);
    }

    private void callerIsOwner() {
        caller(MARIA_PERSON, MARIA);
        lenient()
                .when(callerPermissionsPort.callerHolds("INVITATION_REVOKE_ANY"))
                .thenReturn(true);
    }

    private void callerIsManager() {
        caller(IONUT_PERSON, IONUT);
        lenient()
                .when(callerPermissionsPort.callerHolds("INVITATION_REVOKE_ANY"))
                .thenReturn(false);
    }

    private void caller(PersonId person, MembershipId membership) {
        when(identifyCallerPort.currentCaller())
                .thenReturn(Optional.of(new IdentifyCallerPort.Caller(person, false, true, "WORKSPACE")));
        lenient()
                .when(loadMembershipPort.findByPerson(person))
                .thenReturn(Optional.of(new Membership(membership, person, MembershipStatus.ACTIVE, null)));
        lenient().when(callerPermissionsPort.callerHolds(anyString())).thenReturn(false);
        lenient()
                .when(loadWorkspacePort.load())
                .thenReturn(Workspace.rebuild(WORKSPACE, null, null, NOW.minusSeconds(86400)));
    }

    private void invitationIs(Invitation invitation) {
        when(loadInvitationPort.findByIdForUpdate(INVITATION)).thenReturn(Optional.of(invitation));
        lenient().when(saveInvitationPort.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private static Invitation sentBy(MembershipId inviter) {
        return invitation(InvitationState.SENT, inviter);
    }

    private static Invitation inState(InvitationState state) {
        return invitation(state, MARIA);
    }

    private static Invitation invitation(InvitationState state, MembershipId inviter) {
        return new Invitation(
                INVITATION,
                WORKSPACE,
                new EmailAddress("stefan@atelier.ro"),
                InvitedRole.EMPLOYEE,
                MARIA,
                inviter,
                state,
                "irrelevant-hash",
                NOW.plusSeconds(604800),
                NOW.minusSeconds(3600),
                null);
    }
}
