package com.flowops.workspace.application.describedirectory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import com.flowops.workspace.application.shared.port.DescribePeoplePort;
import com.flowops.workspace.application.shared.port.LoadMembershipPort;
import com.flowops.workspace.application.shared.port.LoadWorkspacePort;
import com.flowops.workspace.domain.enums.MembershipStatus;
import com.flowops.workspace.domain.model.Membership;
import com.flowops.workspace.domain.model.MembershipId;
import com.flowops.workspace.domain.model.PersonId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("TASK-CREATE-01")
@ExtendWith(MockitoExtension.class)
class DescribeDirectoryServiceTest {
    private static final PersonId MARIA = PersonId.of(UUID.randomUUID());
    private static final PersonId IONUT = PersonId.of(UUID.randomUUID());
    private static final PersonId ANDREI = PersonId.of(UUID.randomUUID());

    private static final MembershipId MARIA_ROW = new MembershipId(UUID.randomUUID());
    private static final MembershipId IONUT_ROW = new MembershipId(UUID.randomUUID());
    private static final MembershipId ANDREI_ROW = new MembershipId(UUID.randomUUID());

    @Mock
    private LoadMembershipPort loadMembershipPort;

    @Mock
    private DescribePeoplePort describePeoplePort;

    @Mock
    private LoadWorkspacePort loadWorkspacePort;

    private DescribeDirectoryService service;

    @BeforeEach
    void buildTheService() {
        service = new DescribeDirectoryService(loadMembershipPort, describePeoplePort, loadWorkspacePort);
    }

    @Test
    void anErasedPersonIsNotDescribedEvenIfAuthStillHasANameForThem() {
        when(loadMembershipPort.listAll()).thenReturn(List.of(erased(ANDREI_ROW, ANDREI), active(MARIA_ROW, MARIA)));
        when(describePeoplePort.describe(anyCollection()))
                .thenReturn(List.of(new DescribePeoplePort.PersonDescription(ANDREI, "Andrei Munteanu", "EMPLOYEE")));

        assertThat(service.describe(ANDREI.value()))
                .as("an erased account is nobody, whatever AUTH still holds")
                .isEmpty();
    }

    @Test
    void somebodyDeactivatedIsStillDescribedAndSaysSo() {
        when(loadMembershipPort.listAll()).thenReturn(List.of(deactivated(ANDREI_ROW, ANDREI)));
        when(describePeoplePort.describe(anyCollection()))
                .thenReturn(List.of(new DescribePeoplePort.PersonDescription(ANDREI, "Andrei Munteanu", "EMPLOYEE")));

        DescribeDirectoryUseCase.Person described =
                service.describe(ANDREI.value()).orElseThrow();

        assertThat(described.displayName()).isEqualTo("Andrei Munteanu");
        assertThat(described.active())
                .as("their name still appears against the work they did; their access has ended")
                .isFalse();
    }

    @Test
    void theRootMayDirectAnybodyInTheWorkspace() {
        when(loadMembershipPort.listAll())
                .thenReturn(List.of(active(MARIA_ROW, MARIA), under(ANDREI_ROW, ANDREI, MARIA_ROW)));

        assertThat(service.isWithinScopeOf(MARIA.value(), ANDREI.value())).isTrue();
    }

    @Test
    void anybodyMayDirectTheirOwnWork() {
        assertThat(service.isWithinScopeOf(IONUT.value(), IONUT.value()))
                .as("self-assignment is permitted, and the tree is not even read to answer it")
                .isTrue();
    }

    @Test
    void aManagerReachesTheWholeSubtreeBelowThemAndNothingBeside() {
        when(loadMembershipPort.listAll())
                .thenReturn(List.of(
                        active(MARIA_ROW, MARIA),
                        under(IONUT_ROW, IONUT, MARIA_ROW),
                        under(ANDREI_ROW, ANDREI, IONUT_ROW)));

        assertThat(service.isWithinScopeOf(IONUT.value(), ANDREI.value())).isTrue();
        assertThat(service.isWithinScopeOf(IONUT.value(), MARIA.value()))
                .as("upward is not inside")
                .isFalse();
    }

    @Test
    void nobodyMayDirectSomebodyWhoIsNotHere() {
        when(loadMembershipPort.listAll()).thenReturn(List.of(active(MARIA_ROW, MARIA)));

        assertThat(service.isWithinScopeOf(MARIA.value(), UUID.randomUUID())).isFalse();
    }

    private static Membership active(MembershipId id, PersonId person) {
        return new Membership(id, person, MembershipStatus.ACTIVE, null, null);
    }

    private static Membership under(MembershipId id, PersonId person, MembershipId manager) {
        return new Membership(id, person, MembershipStatus.ACTIVE, manager, null);
    }

    private static Membership deactivated(MembershipId id, PersonId person) {
        return new Membership(id, person, MembershipStatus.DEACTIVATED, null, Instant.parse("2026-08-01T09:00:00Z"));
    }

    private static Membership erased(MembershipId id, PersonId person) {
        return new Membership(id, person, MembershipStatus.ERASED, null, Instant.parse("2026-08-01T09:00:00Z"));
    }
}
