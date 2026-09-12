package com.flowops.workspace.application.eraseperson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.flowops.workspace.application.shared.exception.ConfirmationNameMismatchException;
import com.flowops.workspace.application.shared.exception.MembershipNotFoundException;
import com.flowops.workspace.application.shared.exception.OnlyOwnerException;
import com.flowops.workspace.application.shared.exception.ReauthenticationRequiredException;
import com.flowops.workspace.application.shared.exception.SubjectNotDeactivatedException;
import com.flowops.workspace.application.shared.port.AnonymisePersonPort;
import com.flowops.workspace.application.shared.port.AppendWorkspaceEventPort;
import com.flowops.workspace.application.shared.port.DescribeAccountPort;
import com.flowops.workspace.application.shared.port.DescribePeoplePort;
import com.flowops.workspace.application.shared.port.EraseInvitationTracesPort;
import com.flowops.workspace.application.shared.port.IdentifyCallerPort;
import com.flowops.workspace.application.shared.port.LoadMembershipPort;
import com.flowops.workspace.application.shared.port.LoadWorkspacePort;
import com.flowops.workspace.application.shared.port.LockWorkspaceStructurePort;
import com.flowops.workspace.application.shared.port.RequireReauthenticationPort;
import com.flowops.workspace.application.shared.port.SaveMembershipPort;
import com.flowops.workspace.domain.enums.MembershipStatus;
import com.flowops.workspace.domain.enums.WorkspaceAction;
import com.flowops.workspace.domain.event.WorkspaceEvent;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("WORKSPACE-ERASE-PERSON-01")
@ExtendWith(MockitoExtension.class)
class ErasePersonServiceTest {
    private static final WorkspaceId WORKSPACE = WorkspaceId.of(UUID.randomUUID());
    private static final Instant NOW = Instant.parse("2026-08-10T09:00:00Z");

    private static final PersonId MARIA_PERSON = PersonId.of(UUID.randomUUID());
    private static final PersonId IONUT_PERSON = PersonId.of(UUID.randomUUID());
    private static final PersonId IOANA_PERSON = PersonId.of(UUID.randomUUID());

    private static final MembershipId MARIA = MembershipId.of(UUID.randomUUID());
    private static final MembershipId IONUT = MembershipId.of(UUID.randomUUID());
    private static final MembershipId IOANA = MembershipId.of(UUID.randomUUID());

    @Mock
    private IdentifyCallerPort identifyCallerPort;

    @Mock
    private RequireReauthenticationPort requireReauthenticationPort;

    @Mock
    private LockWorkspaceStructurePort lockWorkspaceStructurePort;

    @Mock
    private LoadMembershipPort loadMembershipPort;

    @Mock
    private DescribePeoplePort describePeoplePort;

    @Mock
    private SaveMembershipPort saveMembershipPort;

    @Mock
    private AnonymisePersonPort anonymisePersonPort;

    @Mock
    private DescribeAccountPort describeAccountPort;

    @Mock
    private EraseInvitationTracesPort eraseInvitationTracesPort;

    @Mock
    private AppendWorkspaceEventPort appendWorkspaceEventPort;

    @Mock
    private LoadWorkspacePort loadWorkspacePort;

    private ErasePersonService service;

    @BeforeEach
    void mariaIsSignedIn() {
        service = new ErasePersonService(
                identifyCallerPort,
                requireReauthenticationPort,
                lockWorkspaceStructurePort,
                loadMembershipPort,
                describePeoplePort,
                saveMembershipPort,
                anonymisePersonPort,
                describeAccountPort,
                eraseInvitationTracesPort,
                appendWorkspaceEventPort,
                loadWorkspacePort,
                Clock.fixed(NOW, ZoneOffset.UTC));

        lenient()
                .when(identifyCallerPort.currentCaller())
                .thenReturn(Optional.of(new IdentifyCallerPort.Caller(MARIA_PERSON, true, true, "HOME")));
        lenient().when(loadWorkspacePort.load()).thenReturn(workspace());

        lenient()
                .when(describeAccountPort.describe(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.of(new DescribeAccountPort.Account(
                        IONUT_PERSON,
                        "ionut@atelier.ro",
                        Optional.of("Ionuț Petrescu"),
                        "MANAGER",
                        "ACTIVE",
                        NOW,
                        List.of())));
    }

    @Test
    void theOwnerErasesADeactivatedColleagueAndTheEventNamesThem() {
        theWorkspaceHolds(maria(), deactivated(IONUT, IONUT_PERSON));
        describedAs(person(MARIA_PERSON, "Maria Ionescu", "OWNER"), person(IONUT_PERSON, "Ionuț Petrescu", "MANAGER"));

        ErasePersonResult result = service.execute(new ErasePersonCommand(IONUT, "Ionuț Petrescu"));

        assertThat(result.changed()).isTrue();
        assertThat(result.opaqueIdentifier()).isEqualTo(IONUT_PERSON);
        verify(anonymisePersonPort).anonymise(IONUT_PERSON);
        verify(saveMembershipPort).erase(IONUT, NOW);

        ArgumentCaptor<WorkspaceEvent> appended = ArgumentCaptor.forClass(WorkspaceEvent.class);
        verify(appendWorkspaceEventPort).append(appended.capture());
        assertThat(appended.getValue().action()).isEqualTo(WorkspaceAction.PERSON_ERASED);
        assertThat(appended.getValue().actor()).isEqualTo(MARIA_PERSON);
        assertThat(appended.getValue().subject()).isEqualTo(IONUT_PERSON);
    }

    @Test
    void theLastOwnerIsRefusedEvenThoughNoSequenceOfRequestsCouldReachThisState() {
        theWorkspaceHolds(deactivated(MARIA, MARIA_PERSON), active(IONUT, IONUT_PERSON, MARIA));
        describedAs(person(MARIA_PERSON, "Maria Ionescu", "OWNER"), person(IONUT_PERSON, "Ionuț Petrescu", "MANAGER"));

        assertThatThrownBy(() -> service.execute(new ErasePersonCommand(MARIA, "Maria Ionescu")))
                .isInstanceOf(OnlyOwnerException.class);

        verifyNoInteractions(anonymisePersonPort);
        verify(saveMembershipPort, never()).erase(any(), any());
    }

    @Test
    void somebodyStillActiveIsRefusedBeforeTheCallerIsAskedForTheirPassword() {
        theWorkspaceHolds(maria(), active(IONUT, IONUT_PERSON, MARIA));
        describedAs(person(MARIA_PERSON, "Maria Ionescu", "OWNER"), person(IONUT_PERSON, "Ionuț Petrescu", "MANAGER"));

        assertThatThrownBy(() -> service.execute(new ErasePersonCommand(IONUT, "Ionuț Petrescu")))
                .isInstanceOf(SubjectNotDeactivatedException.class);

        verifyNoInteractions(requireReauthenticationPort);
        verifyNoInteractions(anonymisePersonPort);
    }

    @Test
    void aStaleSessionIsChallengedBeforeTheTypedNameIsLookedAt() {
        theWorkspaceHolds(maria(), deactivated(IONUT, IONUT_PERSON));
        describedAs(person(MARIA_PERSON, "Maria Ionescu", "OWNER"), person(IONUT_PERSON, "Ionuț Petrescu", "MANAGER"));
        org.mockito.Mockito.doThrow(new ReauthenticationRequiredException())
                .when(requireReauthenticationPort)
                .requireRecentReauthentication();

        assertThatThrownBy(() -> service.execute(new ErasePersonCommand(IONUT, "the wrong name entirely")))
                .isInstanceOf(ReauthenticationRequiredException.class);

        verifyNoInteractions(anonymisePersonPort);
    }

    @Test
    void typingAColleaguesNameDestroysNothing() {
        theWorkspaceHolds(maria(), deactivated(IONUT, IONUT_PERSON), active(IOANA, IOANA_PERSON, MARIA));
        describedAs(
                person(MARIA_PERSON, "Maria Ionescu", "OWNER"),
                person(IONUT_PERSON, "Ionuț Petrescu", "MANAGER"),
                person(IOANA_PERSON, "Ioana Radu", "EMPLOYEE"));

        assertThatThrownBy(() -> service.execute(new ErasePersonCommand(IONUT, "Ioana Radu")))
                .isInstanceOf(ConfirmationNameMismatchException.class);

        verifyNoInteractions(anonymisePersonPort);
        verify(appendWorkspaceEventPort, never()).append(any());
    }

    @Test
    void theRightNameWithStraySpacingAndCasingIsAccepted() {
        theWorkspaceHolds(maria(), deactivated(IONUT, IONUT_PERSON));
        describedAs(person(MARIA_PERSON, "Maria Ionescu", "OWNER"), person(IONUT_PERSON, "Ionuț Petrescu", "MANAGER"));

        assertThat(service.execute(new ErasePersonCommand(IONUT, "  ionuț petrescu "))
                        .changed())
                .isTrue();
    }

    @Test
    void erasingSomebodyAlreadyErasedIsAnsweredWithoutTouchingAnything() {
        theWorkspaceHolds(maria(), erased(IONUT, IONUT_PERSON));

        ErasePersonResult result = service.execute(new ErasePersonCommand(IONUT, "does not matter"));

        assertThat(result.changed()).isFalse();
        assertThat(result.opaqueIdentifier()).isEqualTo(IONUT_PERSON);
        verifyNoInteractions(anonymisePersonPort, requireReauthenticationPort, appendWorkspaceEventPort);
        verify(saveMembershipPort, never()).erase(any(), any());
    }

    @Test
    void anIdentifierNamingNobodyIsNotFound() {
        theWorkspaceHolds(maria());

        assertThatThrownBy(() -> service.execute(new ErasePersonCommand(IONUT, "anybody")))
                .isInstanceOf(MembershipNotFoundException.class);
    }

    @Test
    void theStructureIsLockedBeforeAnythingIsRead() {
        theWorkspaceHolds(maria(), deactivated(IONUT, IONUT_PERSON));
        describedAs(person(MARIA_PERSON, "Maria Ionescu", "OWNER"), person(IONUT_PERSON, "Ionuț Petrescu", "MANAGER"));

        service.execute(new ErasePersonCommand(IONUT, "Ionuț Petrescu"));

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(lockWorkspaceStructurePort, loadMembershipPort);
        order.verify(lockWorkspaceStructurePort).lockForStructuralChange();
        order.verify(loadMembershipPort).listAll();
    }

    private void theWorkspaceHolds(Membership... memberships) {
        lenient().when(loadMembershipPort.listAll()).thenReturn(List.of(memberships));
    }

    private void describedAs(DescribePeoplePort.PersonDescription... descriptions) {
        lenient().when(describePeoplePort.describe(anyCollection())).thenReturn(List.of(descriptions));
    }

    private static DescribePeoplePort.PersonDescription person(PersonId id, String name, String role) {
        return new DescribePeoplePort.PersonDescription(id, name, role);
    }

    private static Membership maria() {
        return new Membership(MARIA, MARIA_PERSON, MembershipStatus.ACTIVE, null);
    }

    private static Membership active(MembershipId id, PersonId person, MembershipId manager) {
        return new Membership(id, person, MembershipStatus.ACTIVE, manager);
    }

    private static Membership deactivated(MembershipId id, PersonId person) {
        return new Membership(id, person, MembershipStatus.DEACTIVATED, MARIA, NOW);
    }

    private static Membership erased(MembershipId id, PersonId person) {
        return new Membership(id, person, MembershipStatus.ERASED, MARIA, NOW);
    }

    private static Workspace workspace() {
        return Workspace.rebuild(WORKSPACE, null, null, NOW);
    }
}
