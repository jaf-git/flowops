package com.flowops.workspace.application.reassignreportingline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowops.workspace.application.shared.exception.NotAuthenticatedException;
import com.flowops.workspace.application.shared.port.AppendWorkspaceEventPort;
import com.flowops.workspace.application.shared.port.DescribePeoplePort;
import com.flowops.workspace.application.shared.port.IdentifyCallerPort;
import com.flowops.workspace.application.shared.port.LoadMembershipPort;
import com.flowops.workspace.application.shared.port.LoadWorkspacePort;
import com.flowops.workspace.application.shared.port.LockWorkspaceStructurePort;
import com.flowops.workspace.application.shared.port.ReassignManagerPort;
import com.flowops.workspace.domain.enums.MembershipStatus;
import com.flowops.workspace.domain.enums.WorkspaceAction;
import com.flowops.workspace.domain.event.WorkspaceEvent;
import com.flowops.workspace.domain.model.Membership;
import com.flowops.workspace.domain.model.MembershipId;
import com.flowops.workspace.domain.model.PersonId;
import com.flowops.workspace.domain.model.Workspace;
import com.flowops.workspace.domain.model.WorkspaceId;
import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
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

@Tag("WORKSPACE-EDIT-REPORTING-LINE-01")
@ExtendWith(MockitoExtension.class)
class ReassignReportingLineServiceTest {
    private static final WorkspaceId WORKSPACE = WorkspaceId.of(UUID.randomUUID());
    private static final Instant NOW = Instant.parse("2026-08-04T09:00:00Z");

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
    private ReassignManagerPort reassignManagerPort;

    @Mock
    private AppendWorkspaceEventPort appendWorkspaceEventPort;

    @Mock
    private LoadWorkspacePort loadWorkspacePort;

    private ReassignReportingLineService service;

    @BeforeEach
    void setUp() {
        service = new ReassignReportingLineService(
                identifyCallerPort,
                lockWorkspaceStructurePort,
                loadMembershipPort,
                describePeoplePort,
                reassignManagerPort,
                appendWorkspaceEventPort,
                loadWorkspacePort,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void theEventNamesThePersonMovedAndBothManagers() {
        theCompanyIsBuilt();

        ReassignReportingLineResult result = service.execute(new ReassignReportingLineCommand(IOANA, MARIA));

        assertThat(result.changed()).isTrue();
        verify(reassignManagerPort).reassign(IOANA, MARIA);

        ArgumentCaptor<WorkspaceEvent> event = ArgumentCaptor.forClass(WorkspaceEvent.class);
        verify(appendWorkspaceEventPort).append(event.capture());
        assertThat(event.getValue().action()).isEqualTo(WorkspaceAction.REPORTING_LINE_CHANGED);
        assertThat(event.getValue().actor()).isEqualTo(MARIA_PERSON);
        assertThat(event.getValue().subject()).isEqualTo(IOANA_PERSON);
        assertThat(event.getValue().formerManager()).isEqualTo(IONUT_PERSON);
        assertThat(event.getValue().newManager()).isEqualTo(MARIA_PERSON);
        assertThat(event.getValue().occurredAt()).isEqualTo(NOW);
    }

    @Test
    void nothingIsWrittenOrRecordedWhenTheProposedManagerIsAlreadyTheirs() {
        theCompanyIsBuilt();

        ReassignReportingLineResult result = service.execute(new ReassignReportingLineCommand(IOANA, IONUT));

        assertThat(result.changed()).isFalse();
        verify(reassignManagerPort, never()).reassign(any(), any());
        verify(appendWorkspaceEventPort, never()).append(any());
    }

    @Test
    void anEventCanCarryNoNameNoAddressAndNoFreeText() {
        List<Class<?>> mayAppearInAnEvent =
                List.of(UUID.class, WorkspaceAction.class, PersonId.class, WorkspaceId.class, Instant.class);

        List<Class<?>> components = Arrays.stream(WorkspaceEvent.class.getRecordComponents())
                .map(RecordComponent::getType)
                .toList();

        assertThat(components)
                .as("an event holds identifiers, an action and an instant — a String is somewhere a name can be put")
                .isSubsetOf(mayAppearInAnEvent);
    }

    @Test
    void anEventForAMoveCannotBeBuiltWithoutNamingEverybody() {
        assertThatThrownBy(() -> new WorkspaceEvent(
                        UUID.randomUUID(),
                        WorkspaceAction.REPORTING_LINE_CHANGED,
                        MARIA_PERSON,
                        IOANA_PERSON,
                        null,
                        MARIA_PERSON,
                        WORKSPACE,
                        NOW))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                        WorkspaceEvent.byActor(WorkspaceAction.REPORTING_LINE_CHANGED, MARIA_PERSON, WORKSPACE, NOW))
                .as("the generic factory cannot be used to sidestep it")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theStructureIsHeldBeforeTheTreeIsRead() {
        theCompanyIsBuilt();

        service.execute(new ReassignReportingLineCommand(IOANA, MARIA));

        InOrder ordered = inOrder(lockWorkspaceStructurePort, loadMembershipPort);
        ordered.verify(lockWorkspaceStructurePort).lockForStructuralChange();
        ordered.verify(loadMembershipPort).listAll();
    }

    @Test
    void anAnonymousCallerIsRefusedBeforeAnythingIsRead() {
        when(identifyCallerPort.currentCaller()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(new ReassignReportingLineCommand(IOANA, MARIA)))
                .isInstanceOf(NotAuthenticatedException.class);

        verify(loadMembershipPort, never()).listAll();
        verify(appendWorkspaceEventPort, never()).append(any());
    }

    private void theCompanyIsBuilt() {
        when(identifyCallerPort.currentCaller())
                .thenReturn(Optional.of(new IdentifyCallerPort.Caller(MARIA_PERSON, false, true, "WORKSPACE")));
        lenient()
                .when(loadMembershipPort.listAll())
                .thenReturn(List.of(
                        member(MARIA, MARIA_PERSON, null),
                        member(IONUT, IONUT_PERSON, MARIA),
                        member(IOANA, IOANA_PERSON, IONUT),
                        member(ANDREI, ANDREI_PERSON, IOANA)));
        lenient()
                .when(describePeoplePort.describe(any()))
                .thenReturn(List.of(
                        new DescribePeoplePort.PersonDescription(MARIA_PERSON, "Maria Ionescu", "OWNER"),
                        new DescribePeoplePort.PersonDescription(IONUT_PERSON, "Ionuț Petrescu", "MANAGER"),
                        new DescribePeoplePort.PersonDescription(IOANA_PERSON, "Ioana Radu", "MANAGER"),
                        new DescribePeoplePort.PersonDescription(ANDREI_PERSON, "Andrei Munteanu", "EMPLOYEE")));
        lenient()
                .when(loadWorkspacePort.load())
                .thenReturn(Workspace.rebuild(WORKSPACE, null, null, NOW.minusSeconds(86400)));
    }

    private static Membership member(MembershipId id, PersonId person, MembershipId manager) {
        return new Membership(id, person, MembershipStatus.ACTIVE, manager);
    }
}
