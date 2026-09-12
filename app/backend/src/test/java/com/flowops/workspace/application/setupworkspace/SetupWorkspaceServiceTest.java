package com.flowops.workspace.application.setupworkspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowops.workspace.application.shared.exception.NotAuthenticatedException;
import com.flowops.workspace.application.shared.exception.OwnerNameRequiredException;
import com.flowops.workspace.application.shared.exception.SetupNotPermittedException;
import com.flowops.workspace.application.shared.port.AppendWorkspaceEventPort;
import com.flowops.workspace.application.shared.port.IdentifyCallerPort;
import com.flowops.workspace.application.shared.port.LoadWorkspacePort;
import com.flowops.workspace.application.shared.port.LoadWorkspaceSettingsPort;
import com.flowops.workspace.application.shared.port.MarkSetupCompletePort;
import com.flowops.workspace.application.shared.port.SaveMembershipPort;
import com.flowops.workspace.application.shared.port.SaveWorkspacePort;
import com.flowops.workspace.application.shared.port.SaveWorkspaceSettingsPort;
import com.flowops.workspace.application.shared.port.SetDisplayNamePort;
import com.flowops.workspace.domain.enums.WorkspaceAction;
import com.flowops.workspace.domain.enums.WorkspaceUse;
import com.flowops.workspace.domain.event.WorkspaceEvent;
import com.flowops.workspace.domain.exception.UnknownTimezoneException;
import com.flowops.workspace.domain.exception.WorkspaceNameRequiredException;
import com.flowops.workspace.domain.model.Membership;
import com.flowops.workspace.domain.model.PersonId;
import com.flowops.workspace.domain.model.Timezone;
import com.flowops.workspace.domain.model.Workspace;
import com.flowops.workspace.domain.model.WorkspaceId;
import com.flowops.workspace.domain.model.WorkspaceName;
import com.flowops.workspace.domain.model.WorkspaceSettings;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

@Tag("WORKSPACE-SETUP-01")
@ExtendWith(MockitoExtension.class)
class SetupWorkspaceServiceTest {
    private static final WorkspaceId WORKSPACE = WorkspaceId.of(UUID.randomUUID());
    private static final PersonId MARIA = PersonId.of(UUID.randomUUID());
    private static final Instant NOW = Instant.parse("2026-08-03T10:15:00Z");
    private static final Instant SEEDED_AT = Instant.parse("2026-08-01T09:00:00Z");

    @Mock
    private IdentifyCallerPort identifyCallerPort;

    @Mock
    private LoadWorkspacePort loadWorkspacePort;

    @Mock
    private SaveWorkspacePort saveWorkspacePort;

    @Mock
    private LoadWorkspaceSettingsPort loadWorkspaceSettingsPort;

    @Mock
    private SaveWorkspaceSettingsPort saveWorkspaceSettingsPort;

    @Mock
    private SetDisplayNamePort setDisplayNamePort;

    @Mock
    private MarkSetupCompletePort markSetupCompletePort;

    @Mock
    private SaveMembershipPort saveMembershipPort;

    @Mock
    private AppendWorkspaceEventPort appendWorkspaceEventPort;

    private SetupWorkspaceService service;

    @BeforeEach
    void buildTheService() {
        service = new SetupWorkspaceService(
                identifyCallerPort,
                loadWorkspacePort,
                saveWorkspacePort,
                loadWorkspaceSettingsPort,
                saveWorkspaceSettingsPort,
                setDisplayNamePort,
                markSetupCompletePort,
                saveMembershipPort,
                appendWorkspaceEventPort,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void anOwnerWithSetupIncomplete() {
        when(identifyCallerPort.currentCaller())
                .thenReturn(Optional.of(new IdentifyCallerPort.Caller(MARIA, true, false, "WORKSPACE_SETUP")));
        when(loadWorkspacePort.load()).thenReturn(Workspace.rebuild(WORKSPACE, null, null, SEEDED_AT));
    }

    private void anOwnerWhoWillFinishSetup() {
        anOwnerWithSetupIncomplete();
        when(markSetupCompletePort.markCallerSetupComplete()).thenReturn("TRIAGE");
    }

    private static SetupWorkspaceCommand mariasAnswers() {
        return new SetupWorkspaceCommand("Maria Ionescu", "Atelier Ionescu", WorkspaceUse.WORK, "Europe/Bucharest");
    }

    @Test
    void itStoresTheNameTheUseAndTheTimezoneAndMarksSetupComplete() {
        anOwnerWhoWillFinishSetup();

        SetupWorkspaceResult result = service.execute(mariasAnswers());

        ArgumentCaptor<Workspace> named = ArgumentCaptor.forClass(Workspace.class);
        verify(saveWorkspacePort).save(named.capture());
        assertThat(named.getValue().name()).contains(new WorkspaceName("Atelier Ionescu"));
        assertThat(named.getValue().use()).contains(WorkspaceUse.WORK);

        ArgumentCaptor<WorkspaceSettings> settings = ArgumentCaptor.forClass(WorkspaceSettings.class);
        verify(saveWorkspaceSettingsPort).save(settings.capture());
        assertThat(settings.getValue().timezone()).isEqualTo(new Timezone("Europe/Bucharest"));

        verify(setDisplayNamePort).setCallerDisplayName("Maria Ionescu");
        verify(markSetupCompletePort).markCallerSetupComplete();
        assertThat(result.landingTarget()).isEqualTo("TRIAGE");
    }

    @Test
    void theFirstSettingsRowIsEffectiveDatedAndInForce() {
        anOwnerWhoWillFinishSetup();

        service.execute(mariasAnswers());

        ArgumentCaptor<WorkspaceSettings> settings = ArgumentCaptor.forClass(WorkspaceSettings.class);
        verify(saveWorkspaceSettingsPort).save(settings.capture());
        assertThat(settings.getValue().effectiveFrom()).isEqualTo(NOW);
        assertThat(settings.getValue().effectiveTo()).isEmpty();
    }

    @Test
    void itAppendsExactlyOneSetupCompletedEvent() {
        anOwnerWhoWillFinishSetup();

        service.execute(mariasAnswers());

        ArgumentCaptor<WorkspaceEvent> appended = ArgumentCaptor.forClass(WorkspaceEvent.class);
        verify(appendWorkspaceEventPort, times(1)).append(appended.capture());
        assertThat(appended.getValue().action()).isEqualTo(WorkspaceAction.SETUP_COMPLETED);
        assertThat(appended.getValue().occurredAt()).isEqualTo(NOW);
    }

    @Test
    void theEventCarriesAnIdentifierAndNothingPersonal() {
        anOwnerWhoWillFinishSetup();

        service.execute(mariasAnswers());

        ArgumentCaptor<WorkspaceEvent> appended = ArgumentCaptor.forClass(WorkspaceEvent.class);
        verify(appendWorkspaceEventPort).append(appended.capture());
        assertThat(appended.getValue().actor()).isEqualTo(MARIA);
        assertThat(appended.getValue().toString())
                .doesNotContain("Maria Ionescu")
                .doesNotContain("Atelier Ionescu")
                .doesNotContain("maria@atelier.ro");
    }

    @Test
    void theEventIsAppendedAfterEveryOtherWrite() {
        anOwnerWhoWillFinishSetup();

        service.execute(mariasAnswers());

        InOrder order = inOrder(
                setDisplayNamePort,
                saveWorkspacePort,
                saveWorkspaceSettingsPort,
                markSetupCompletePort,
                appendWorkspaceEventPort);
        order.verify(setDisplayNamePort).setCallerDisplayName("Maria Ionescu");
        order.verify(saveWorkspacePort).save(any());
        order.verify(saveWorkspaceSettingsPort).save(any());
        order.verify(markSetupCompletePort).markCallerSetupComplete();
        order.verify(appendWorkspaceEventPort).append(any());
    }

    @Test
    void anEmptyWorkspaceNameIsRefusedAndNothingIsWritten() {
        anOwnerWithSetupIncomplete();

        assertThatThrownBy(() -> service.execute(
                        new SetupWorkspaceCommand("Maria Ionescu", "   ", WorkspaceUse.WORK, "Europe/Bucharest")))
                .isInstanceOf(WorkspaceNameRequiredException.class);

        verify(saveWorkspacePort, never()).save(any());
        verify(saveWorkspaceSettingsPort, never()).save(any());
        verify(markSetupCompletePort, never()).markCallerSetupComplete();
        verifyNoInteractions(appendWorkspaceEventPort);
    }

    @Test
    void anUnknownTimezoneIsRefusedAndNothingIsWritten() {
        anOwnerWithSetupIncomplete();

        assertThatThrownBy(() -> service.execute(new SetupWorkspaceCommand(
                        "Maria Ionescu", "Atelier Ionescu", WorkspaceUse.WORK, "Europe/Atlantis")))
                .isInstanceOf(UnknownTimezoneException.class);

        verify(saveWorkspacePort, never()).save(any());
        verify(saveWorkspaceSettingsPort, never()).save(any());
        verify(markSetupCompletePort, never()).markCallerSetupComplete();
        verifyNoInteractions(appendWorkspaceEventPort);
    }

    @Test
    void aRefusedOwnerNameStopsEverythingAfterIt() {
        anOwnerWithSetupIncomplete();
        doThrow(new OwnerNameRequiredException(new IllegalStateException("blank")))
                .when(setDisplayNamePort)
                .setCallerDisplayName("   ");

        assertThatThrownBy(() -> service.execute(
                        new SetupWorkspaceCommand("   ", "Atelier Ionescu", WorkspaceUse.WORK, "Europe/Bucharest")))
                .isInstanceOf(OwnerNameRequiredException.class);

        verify(saveWorkspacePort, never()).save(any());
        verify(markSetupCompletePort, never()).markCallerSetupComplete();
        verifyNoInteractions(appendWorkspaceEventPort);
    }

    @Test
    void aCallerWhoDoesNotOwnSetupIsRefused() {
        when(identifyCallerPort.currentCaller())
                .thenReturn(Optional.of(new IdentifyCallerPort.Caller(MARIA, false, false, "MY_WORK")));

        assertThatThrownBy(() -> service.execute(mariasAnswers())).isInstanceOf(SetupNotPermittedException.class);

        verify(setDisplayNamePort, never()).setCallerDisplayName(any());
        verify(saveWorkspacePort, never()).save(any());
        verifyNoInteractions(appendWorkspaceEventPort);
    }

    @Test
    void aCallerWithNoSessionIsRefused() {
        when(identifyCallerPort.currentCaller()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(mariasAnswers())).isInstanceOf(NotAuthenticatedException.class);

        verifyNoInteractions(appendWorkspaceEventPort);
    }

    @Test
    void aReplayAfterCompletionWritesNothingAndAppendsNothing() {
        when(identifyCallerPort.currentCaller())
                .thenReturn(Optional.of(new IdentifyCallerPort.Caller(MARIA, true, true, "TRIAGE")));
        when(loadWorkspacePort.load())
                .thenReturn(Workspace.rebuild(
                        WORKSPACE, new WorkspaceName("Atelier Ionescu"), WorkspaceUse.WORK, SEEDED_AT));
        when(loadWorkspaceSettingsPort.inForce(WORKSPACE))
                .thenReturn(Optional.of(
                        WorkspaceSettings.inForceFrom(WORKSPACE, new Timezone("Europe/Bucharest"), SEEDED_AT)));

        SetupWorkspaceResult result = service.execute(
                new SetupWorkspaceCommand("Someone Else", "A Different Name", WorkspaceUse.PERSONAL, "Europe/London"));

        assertThat(result.name()).isEqualTo(new WorkspaceName("Atelier Ionescu"));
        assertThat(result.timezone()).isEqualTo(new Timezone("Europe/Bucharest"));
        assertThat(result.landingTarget()).isEqualTo("TRIAGE");
        verify(setDisplayNamePort, never()).setCallerDisplayName(any());
        verify(saveWorkspacePort, never()).save(any());
        verify(saveWorkspaceSettingsPort, never()).save(any());
        verifyNoInteractions(appendWorkspaceEventPort);
    }

    @Test
    void theTwoUseValuesDifferInWhatIsStoredAndInNothingElse() {
        anOwnerWhoWillFinishSetup();
        service.execute(
                new SetupWorkspaceCommand("Maria Ionescu", "Atelier Ionescu", WorkspaceUse.WORK, "Europe/Bucharest"));

        ArgumentCaptor<Workspace> forWork = ArgumentCaptor.forClass(Workspace.class);
        ArgumentCaptor<WorkspaceSettings> workSettings = ArgumentCaptor.forClass(WorkspaceSettings.class);
        ArgumentCaptor<WorkspaceEvent> workEvent = ArgumentCaptor.forClass(WorkspaceEvent.class);
        verify(saveWorkspacePort).save(forWork.capture());
        verify(saveWorkspaceSettingsPort).save(workSettings.capture());
        verify(appendWorkspaceEventPort).append(workEvent.capture());

        buildTheService();
        anOwnerWhoWillFinishSetup();
        service.execute(new SetupWorkspaceCommand(
                "Maria Ionescu", "Atelier Ionescu", WorkspaceUse.PERSONAL, "Europe/Bucharest"));

        ArgumentCaptor<Workspace> forPersonal = ArgumentCaptor.forClass(Workspace.class);
        ArgumentCaptor<WorkspaceSettings> personalSettings = ArgumentCaptor.forClass(WorkspaceSettings.class);
        ArgumentCaptor<WorkspaceEvent> personalEvent = ArgumentCaptor.forClass(WorkspaceEvent.class);
        verify(saveWorkspacePort, times(2)).save(forPersonal.capture());
        verify(saveWorkspaceSettingsPort, times(2)).save(personalSettings.capture());
        verify(appendWorkspaceEventPort, times(2)).append(personalEvent.capture());

        assertThat(forPersonal.getValue().name()).isEqualTo(forWork.getValue().name());
        assertThat(personalSettings.getValue().timezone())
                .isEqualTo(workSettings.getValue().timezone());
        assertThat(personalEvent.getValue().action())
                .isEqualTo(workEvent.getValue().action());
        assertThat(personalEvent.getValue().actor())
                .isEqualTo(workEvent.getValue().actor());
        assertThat(forPersonal.getValue().use()).contains(WorkspaceUse.PERSONAL);
        assertThat(forWork.getValue().use()).contains(WorkspaceUse.WORK);
    }

    @Test
    void setupCreatesTheOwnersMembershipAsTheRootOfTheReportingTree() {
        anOwnerWhoWillFinishSetup();

        service.execute(mariasAnswers());

        ArgumentCaptor<Membership> saved = ArgumentCaptor.forClass(Membership.class);
        verify(saveMembershipPort).save(any(), saved.capture());

        assertThat(saved.getValue().person()).isEqualTo(MARIA);
        assertThat(saved.getValue().isActive()).isTrue();
        assertThat(saved.getValue().managerOrRoot()).isEmpty();
    }
}
