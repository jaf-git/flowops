package com.flowops.workspace.application.deactivateperson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.flowops.workspace.application.shared.exception.OnlyOwnerException;
import com.flowops.workspace.application.shared.port.AppendWorkspaceEventPort;
import com.flowops.workspace.application.shared.port.DescribePeoplePort;
import com.flowops.workspace.application.shared.port.EndPersonSessionsPort;
import com.flowops.workspace.application.shared.port.IdentifyCallerPort;
import com.flowops.workspace.application.shared.port.LoadMembershipPort;
import com.flowops.workspace.application.shared.port.LoadWorkspacePort;
import com.flowops.workspace.application.shared.port.LockWorkspaceStructurePort;
import com.flowops.workspace.application.shared.port.ReassignManagerPort;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("WORKSPACE-DEACTIVATE-PERSON-01")
@ExtendWith(MockitoExtension.class)
class DeactivatePersonServiceTest {
    private static final WorkspaceId WORKSPACE = WorkspaceId.of(UUID.randomUUID());
    private static final Instant NOW = Instant.parse("2026-08-10T09:00:00Z");

    private static final PersonId MARIA_PERSON = PersonId.of(UUID.randomUUID());
    private static final PersonId IONUT_PERSON = PersonId.of(UUID.randomUUID());
    private static final PersonId IOANA_PERSON = PersonId.of(UUID.randomUUID());
    private static final PersonId ANDREI_PERSON = PersonId.of(UUID.randomUUID());

    private static final MembershipId MARIA = MembershipId.of(UUID.randomUUID());
    private static final MembershipId IONUT = MembershipId.of(UUID.randomUUID());
    private static final MembershipId IOANA = MembershipId.of(UUID.randomUUID());
    private static final MembershipId ANDREI = MembershipId.of(UUID.randomUUID());

    @Mock
    private IdentifyCallerPort identifyCallerPort;

    @Mock
    private LockWorkspaceStructurePort lockWorkspaceStructurePort;

    @Mock
    private LoadMembershipPort loadMembershipPort;

    @Mock
    private DescribePeoplePort describePeoplePort;

    @Mock
    private SaveMembershipPort saveMembershipPort;

    @Mock
    private ReassignManagerPort reassignManagerPort;

    @Mock
    private EndPersonSessionsPort endPersonSessionsPort;

    @Mock
    private AppendWorkspaceEventPort appendWorkspaceEventPort;

    @Mock
    private LoadWorkspacePort loadWorkspacePort;

    private DeactivatePersonService service;

    @BeforeEach
    void setUp() {
        service = new DeactivatePersonService(
                identifyCallerPort,
                lockWorkspaceStructurePort,
                loadMembershipPort,
                describePeoplePort,
                saveMembershipPort,
                reassignManagerPort,
                endPersonSessionsPort,
                appendWorkspaceEventPort,
                loadWorkspacePort,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void deactivatingSomebodyEndsTheirMembershipAndEverySessionTheyHold() {
        theCompanyIsBuilt();

        DeactivatePersonResult result = service.execute(new DeactivatePersonCommand(IONUT));

        assertThat(result.changed()).isTrue();
        verify(saveMembershipPort).deactivate(IONUT, NOW);
        verify(saveMembershipPort, never()).save(any(), any());
        verify(endPersonSessionsPort).endEverySessionFor(IONUT_PERSON);
    }

    @Test
    void onlyTheDirectReportsMoveAndTheyMoveToTheDeactivatedPersonsOwnManager() {
        theCompanyIsBuilt();

        DeactivatePersonResult result = service.execute(new DeactivatePersonCommand(IONUT));

        verify(reassignManagerPort).reassign(IOANA, MARIA);
        verify(reassignManagerPort, never()).reassign(ANDREI, MARIA);
        assertThat(result.reportsMoved()).containsExactly(IOANA);
        assertThat(result.reportsMovedTo()).contains(MARIA);
    }

    @Test
    void theEventNamesTheActorTheSubjectAndWhereTheReportsWent() {
        theCompanyIsBuilt();

        service.execute(new DeactivatePersonCommand(IONUT));

        ArgumentCaptor<WorkspaceEvent> event = ArgumentCaptor.forClass(WorkspaceEvent.class);
        verify(appendWorkspaceEventPort).append(event.capture());
        assertThat(event.getValue().action()).isEqualTo(WorkspaceAction.PERSON_DEACTIVATED);
        assertThat(event.getValue().actor()).isEqualTo(MARIA_PERSON);
        assertThat(event.getValue().subject()).isEqualTo(IONUT_PERSON);
        assertThat(event.getValue().newManager()).isEqualTo(MARIA_PERSON);
        assertThat(event.getValue().occurredAt()).isEqualTo(NOW);
    }

    @Test
    void deactivatingSomebodyWithNoReportsMovesNobodyAndClaimsNoDestination() {
        theCompanyIsBuilt();

        DeactivatePersonResult result = service.execute(new DeactivatePersonCommand(ANDREI));

        verify(reassignManagerPort, never()).reassign(any(), any());
        assertThat(result.reportsMoved()).isEmpty();
        assertThat(result.reportsMovedTo()).isEmpty();
        ArgumentCaptor<WorkspaceEvent> event = ArgumentCaptor.forClass(WorkspaceEvent.class);
        verify(appendWorkspaceEventPort).append(event.capture());
        assertThat(event.getValue().newManager()).isNull();
    }

    @Test
    void theLastActiveOwnerIsRefused() {
        theCompanyIsBuilt();

        assertThatThrownBy(() -> service.execute(new DeactivatePersonCommand(MARIA)))
                .isInstanceOf(OnlyOwnerException.class);

        verify(saveMembershipPort, never()).save(any(), any());
        verifyNoInteractions(endPersonSessionsPort, appendWorkspaceEventPort, reassignManagerPort);
    }

    @Test
    void anOwnerIsDeactivatedNormallyWhenAnotherActiveOwnerRemains() {
        thereAreTwoOwners();

        DeactivatePersonResult result = service.execute(new DeactivatePersonCommand(MARIA));

        assertThat(result.changed()).isTrue();
        verify(endPersonSessionsPort).endEverySessionFor(MARIA_PERSON);
    }

    @Test
    void anAlreadyDeactivatedPersonIsAnsweredWithoutWritingOrRecordingAnything() {
        somebodyHasAlreadyLeft();

        DeactivatePersonResult result = service.execute(new DeactivatePersonCommand(IONUT));

        assertThat(result.changed()).isFalse();
        verify(saveMembershipPort, never()).save(any(), any());
        verifyNoInteractions(endPersonSessionsPort, appendWorkspaceEventPort, reassignManagerPort);
    }

    @Test
    void theStructureIsLockedBeforeTheTreeIsRead() {
        theCompanyIsBuilt();

        service.execute(new DeactivatePersonCommand(IONUT));

        InOrder ordered = inOrder(lockWorkspaceStructurePort, loadMembershipPort);
        ordered.verify(lockWorkspaceStructurePort).lockForStructuralChange();
        ordered.verify(loadMembershipPort).listAll();
    }

    private void theCompanyIsBuilt() {
        callerIsMaria();
        lenient()
                .when(loadMembershipPort.listAll())
                .thenReturn(List.of(
                        member(MARIA, MARIA_PERSON, null),
                        member(IONUT, IONUT_PERSON, MARIA),
                        member(IOANA, IOANA_PERSON, IONUT),
                        member(ANDREI, ANDREI_PERSON, IOANA)));
        peopleAre("OWNER", "MANAGER", "MANAGER", "EMPLOYEE");
    }

    private void thereAreTwoOwners() {
        callerIsMaria();
        lenient()
                .when(loadMembershipPort.listAll())
                .thenReturn(List.of(member(MARIA, MARIA_PERSON, null), member(IONUT, IONUT_PERSON, MARIA)));
        lenient()
                .when(describePeoplePort.describe(any()))
                .thenReturn(List.of(
                        new DescribePeoplePort.PersonDescription(MARIA_PERSON, "Maria Ionescu", "OWNER"),
                        new DescribePeoplePort.PersonDescription(IONUT_PERSON, "Ionuț Petrescu", "OWNER")));
        lenient().when(loadWorkspacePort.load()).thenReturn(workspace());
    }

    private void somebodyHasAlreadyLeft() {
        callerIsMaria();
        lenient()
                .when(loadMembershipPort.listAll())
                .thenReturn(List.of(
                        member(MARIA, MARIA_PERSON, null),
                        new Membership(
                                IONUT, IONUT_PERSON, MembershipStatus.DEACTIVATED, MARIA, NOW.minusSeconds(60))));
        lenient()
                .when(describePeoplePort.describe(any()))
                .thenReturn(List.of(
                        new DescribePeoplePort.PersonDescription(MARIA_PERSON, "Maria Ionescu", "OWNER"),
                        new DescribePeoplePort.PersonDescription(IONUT_PERSON, "Ionuț Petrescu", "MANAGER")));
        lenient().when(loadWorkspacePort.load()).thenReturn(workspace());
    }

    private void callerIsMaria() {
        lenient()
                .when(identifyCallerPort.currentCaller())
                .thenReturn(Optional.of(new IdentifyCallerPort.Caller(MARIA_PERSON, false, true, "WORKSPACE")));
    }

    private void peopleAre(String maria, String ionut, String ioana, String andrei) {
        lenient()
                .when(describePeoplePort.describe(any()))
                .thenReturn(List.of(
                        new DescribePeoplePort.PersonDescription(MARIA_PERSON, "Maria Ionescu", maria),
                        new DescribePeoplePort.PersonDescription(IONUT_PERSON, "Ionuț Petrescu", ionut),
                        new DescribePeoplePort.PersonDescription(IOANA_PERSON, "Ioana Radu", ioana),
                        new DescribePeoplePort.PersonDescription(ANDREI_PERSON, "Andrei Munteanu", andrei)));
        lenient().when(loadWorkspacePort.load()).thenReturn(workspace());
    }

    private Workspace workspace() {
        return Workspace.rebuild(WORKSPACE, null, null, NOW.minusSeconds(86400));
    }

    private static Membership member(MembershipId id, PersonId person, MembershipId manager) {
        return new Membership(id, person, MembershipStatus.ACTIVE, manager);
    }
}
