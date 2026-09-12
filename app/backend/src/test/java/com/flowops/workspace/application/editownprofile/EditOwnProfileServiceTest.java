package com.flowops.workspace.application.editownprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.flowops.workspace.application.shared.exception.MemberNameRequiredException;
import com.flowops.workspace.application.shared.port.AppendWorkspaceEventPort;
import com.flowops.workspace.application.shared.port.DescribePeoplePort;
import com.flowops.workspace.application.shared.port.IdentifyCallerPort;
import com.flowops.workspace.application.shared.port.LoadWorkspacePort;
import com.flowops.workspace.application.shared.port.SetDisplayNamePort;
import com.flowops.workspace.domain.enums.WorkspaceAction;
import com.flowops.workspace.domain.event.WorkspaceEvent;
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

@Tag("WORKSPACE-EDIT-OWN-PROFILE-01")
@ExtendWith(MockitoExtension.class)
class EditOwnProfileServiceTest {
    private static final WorkspaceId WORKSPACE = WorkspaceId.of(UUID.randomUUID());
    private static final Instant NOW = Instant.parse("2026-08-10T09:00:00Z");
    private static final PersonId IOANA = PersonId.of(UUID.randomUUID());

    @Mock
    private IdentifyCallerPort identifyCallerPort;

    @Mock
    private DescribePeoplePort describePeoplePort;

    @Mock
    private SetDisplayNamePort setDisplayNamePort;

    @Mock
    private AppendWorkspaceEventPort appendWorkspaceEventPort;

    @Mock
    private LoadWorkspacePort loadWorkspacePort;

    private EditOwnProfileService service;

    @BeforeEach
    void ioanaIsSignedIn() {
        service = new EditOwnProfileService(
                identifyCallerPort,
                describePeoplePort,
                setDisplayNamePort,
                appendWorkspaceEventPort,
                loadWorkspacePort,
                Clock.fixed(NOW, ZoneOffset.UTC));

        lenient()
                .when(identifyCallerPort.currentCaller())
                .thenReturn(Optional.of(new IdentifyCallerPort.Caller(IOANA, false, true, "HOME")));
        lenient().when(loadWorkspacePort.load()).thenReturn(Workspace.rebuild(WORKSPACE, null, null, NOW));
        lenient()
                .when(describePeoplePort.describe(anyCollection()))
                .thenReturn(List.of(new DescribePeoplePort.PersonDescription(IOANA, "Ioana Radu", "EMPLOYEE")));
    }

    @Test
    void theNameIsStoredAndTheEventNamesThePersonAndCarriesNeitherName() {
        EditOwnProfileResult result = service.execute(new EditOwnProfileCommand("Ioana Radu-Marin"));

        assertThat(result.changed()).isTrue();
        assertThat(result.displayName()).isEqualTo("Ioana Radu-Marin");
        verify(setDisplayNamePort).setCallerDisplayName("Ioana Radu-Marin");

        ArgumentCaptor<WorkspaceEvent> appended = ArgumentCaptor.forClass(WorkspaceEvent.class);
        verify(appendWorkspaceEventPort).append(appended.capture());
        WorkspaceEvent event = appended.getValue();
        assertThat(event.action()).isEqualTo(WorkspaceAction.PROFILE_CHANGED);
        assertThat(event.actor()).isEqualTo(IOANA);
        assertThat(event.subject()).isEqualTo(IOANA);
        assertThat(event.toString())
                .as("the ruling: it carries neither the old name nor the new one, so neither can survive an erasure")
                .doesNotContain("Ioana");
    }

    @Test
    void aNameOfNothingButSpacesIsRefusedByTheUseCaseItself() {
        assertThatThrownBy(() -> service.execute(new EditOwnProfileCommand("   ")))
                .isInstanceOf(MemberNameRequiredException.class);

        verifyNoInteractions(setDisplayNamePort, appendWorkspaceEventPort);
    }

    @Test
    void aNameThatIsNothingAtAllIsRefusedTheSameWay() {
        assertThatThrownBy(() -> service.execute(new EditOwnProfileCommand(null)))
                .isInstanceOf(MemberNameRequiredException.class);

        verifyNoInteractions(setDisplayNamePort, appendWorkspaceEventPort);
    }

    @Test
    void theNameTheyAlreadyHaveIsAcceptedAndNothingIsWritten() {
        EditOwnProfileResult result = service.execute(new EditOwnProfileCommand("Ioana Radu"));

        assertThat(result.changed()).isFalse();
        assertThat(result.displayName()).isEqualTo("Ioana Radu");
        verifyNoInteractions(setDisplayNamePort);
        verify(appendWorkspaceEventPort, never()).append(any());
    }

    @Test
    void theSameNameWithStraySpacingIsStillNoChange() {
        assertThat(service.execute(new EditOwnProfileCommand("  Ioana Radu  ")).changed())
                .isFalse();
        verifyNoInteractions(setDisplayNamePort);
    }

    @Test
    void correctingOnlyTheCapitalisationIsARealChange() {
        assertThat(service.execute(new EditOwnProfileCommand("IOANA RADU")).changed())
                .isTrue();
        verify(setDisplayNamePort).setCallerDisplayName("IOANA RADU");
    }
}
