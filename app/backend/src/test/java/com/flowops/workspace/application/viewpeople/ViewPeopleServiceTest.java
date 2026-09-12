package com.flowops.workspace.application.viewpeople;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowops.workspace.application.shared.exception.NotAuthenticatedException;
import com.flowops.workspace.application.shared.exception.SetupNotPermittedException;
import com.flowops.workspace.application.shared.port.CallerPermissionsPort;
import com.flowops.workspace.application.shared.port.DescribePeoplePort;
import com.flowops.workspace.application.shared.port.IdentifyCallerPort;
import com.flowops.workspace.application.shared.port.LoadInvitationPort;
import com.flowops.workspace.application.shared.port.LoadMembershipPort;
import com.flowops.workspace.domain.enums.InvitationState;
import com.flowops.workspace.domain.enums.InvitedRole;
import com.flowops.workspace.domain.enums.MembershipStatus;
import com.flowops.workspace.domain.model.EmailAddress;
import com.flowops.workspace.domain.model.Invitation;
import com.flowops.workspace.domain.model.InvitationId;
import com.flowops.workspace.domain.model.Membership;
import com.flowops.workspace.domain.model.MembershipId;
import com.flowops.workspace.domain.model.PersonId;
import com.flowops.workspace.domain.model.WorkspaceId;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("WORKSPACE-VIEW-PEOPLE-01")
@ExtendWith(MockitoExtension.class)
class ViewPeopleServiceTest {
    private static final WorkspaceId WORKSPACE = WorkspaceId.of(UUID.randomUUID());

    private static final PersonId MARIA_PERSON = PersonId.of(UUID.randomUUID());
    private static final PersonId IONUT_PERSON = PersonId.of(UUID.randomUUID());
    private static final PersonId IOANA_PERSON = PersonId.of(UUID.randomUUID());
    private static final PersonId ANDREI_PERSON = PersonId.of(UUID.randomUUID());

    private static final MembershipId MARIA = MembershipId.of(UUID.randomUUID());
    private static final MembershipId IONUT = MembershipId.of(UUID.randomUUID());
    private static final MembershipId IOANA = MembershipId.of(UUID.randomUUID());
    private static final MembershipId ANDREI = MembershipId.of(UUID.randomUUID());

    private static final Instant NOW = Instant.parse("2026-08-04T09:00:00Z");
    private static final Instant DEACTIVATED_ON = Instant.parse("2026-07-02T09:00:00Z");

    @Mock
    private IdentifyCallerPort identifyCallerPort;

    @Mock
    private LoadMembershipPort loadMembershipPort;

    @Mock
    private LoadInvitationPort loadInvitationPort;

    @Mock
    private DescribePeoplePort describePeoplePort;

    @Mock
    private CallerPermissionsPort callerPermissionsPort;

    private ViewPeopleService service;

    @BeforeEach
    void setUp() {
        service = new ViewPeopleService(
                identifyCallerPort, loadMembershipPort, loadInvitationPort, describePeoplePort, callerPermissionsPort);
    }

    @Test
    void everyActiveMemberAppearsWithTheirRoleAndTheManagerTheyReportTo() {
        callerIs(MARIA_PERSON);
        wholeWorkspaceLoaded();
        seesNoInvitations();

        ViewPeopleResult result = service.execute();

        assertThat(result.people())
                .extracting(ViewPeopleResult.Person::displayName, ViewPeopleResult.Person::role)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("Maria Ionescu", "OWNER"),
                        org.assertj.core.groups.Tuple.tuple("Ionuț Petrescu", "MANAGER"),
                        org.assertj.core.groups.Tuple.tuple("Ioana Radu", "EMPLOYEE"));

        assertThat(personNamed(result, "Ioana Radu").manager()).contains(IONUT);
        assertThat(personNamed(result, "Ionuț Petrescu").manager()).contains(MARIA);
    }

    @Test
    void theOwnerIsTheRootAndReportsToNobody() {
        callerIs(MARIA_PERSON);
        wholeWorkspaceLoaded();
        seesNoInvitations();

        ViewPeopleResult result = service.execute();

        assertThat(personNamed(result, "Maria Ionescu").manager()).isEmpty();
        assertThat(result.people())
                .filteredOn(person -> person.manager().isEmpty())
                .hasSize(1);
    }

    @Test
    void theViewersOwnEntryIsMarkedSoTheyCanFindThemselves() {
        callerIs(IOANA_PERSON);
        wholeWorkspaceLoaded();
        seesNoInvitations();

        ViewPeopleResult result = service.execute();

        assertThat(result.people())
                .filteredOn(ViewPeopleResult.Person::isSelf)
                .singleElement()
                .satisfies(person -> assertThat(person.displayName()).isEqualTo("Ioana Radu"));
    }

    @Test
    void aDeactivatedPersonIsReturnedCarryingTheStatusThatLetsTheScreenHideThem() {
        callerIs(MARIA_PERSON);
        wholeWorkspaceLoaded();
        seesNoInvitations();

        ViewPeopleResult result = service.execute();

        assertThat(personNamed(result, "Andrei Munteanu").status()).isEqualTo(MembershipStatus.DEACTIVATED);
        assertThat(personNamed(result, "Andrei Munteanu").deactivatedAt())
                .as("extension 2c marks them deactivated *with the date*")
                .contains(DEACTIVATED_ON);
        assertThat(result.people())
                .filteredOn(person -> person.status() == MembershipStatus.ACTIVE)
                .hasSize(3);
    }

    @Test
    void aHolderOfPersonInviteAlsoSeesTheInvitationsNobodyHasAcceptedYet() {
        callerIs(MARIA_PERSON);
        wholeWorkspaceLoaded();
        seesInvitations(openInvitationTo("stefan@atelierlemn.ro", IONUT));

        ViewPeopleResult result = service.execute();

        assertThat(result.invitations()).isPresent();
        assertThat(result.invitations().orElseThrow()).singleElement().satisfies(invitation -> {
            assertThat(invitation.email().value()).isEqualTo("stefan@atelierlemn.ro");
            assertThat(invitation.intendedRole()).isEqualTo(InvitedRole.EMPLOYEE);
            assertThat(invitation.intendedManager()).isEqualTo(IONUT);
            assertThat(invitation.state()).isEqualTo(InvitationState.SENT);
        });
    }

    @Test
    void withoutPersonInviteNoInvitationIsReturnedAndNoneIsEvenRead() {
        callerIs(IOANA_PERSON);
        wholeWorkspaceLoaded();
        when(callerPermissionsPort.callerHolds("PERSON_INVITE")).thenReturn(false);

        ViewPeopleResult result = service.execute();

        assertThat(result.invitations()).isEmpty();
        verify(loadInvitationPort, never()).listOpen();
    }

    @Test
    void aManagerSeesOnlyTheInvitationsTheyCreatedThemselves() {
        callerIs(IONUT_PERSON);
        wholeWorkspaceLoaded();
        seesInvitations(
                openInvitationTo("stefan@atelierlemn.ro", IONUT), openInvitationTo("elena@atelierlemn.ro", MARIA));

        ViewPeopleResult result = service.execute();

        assertThat(result.invitations().orElseThrow())
                .singleElement()
                .satisfies(invitation -> assertThat(invitation.email().value()).isEqualTo("stefan@atelierlemn.ro"));
    }

    @Test
    void theOwnerSeesEveryInvitationWhoeverCreatedIt() {
        callerIs(MARIA_PERSON);
        wholeWorkspaceLoaded();
        seesInvitations(
                openInvitationTo("stefan@atelierlemn.ro", IONUT), openInvitationTo("elena@atelierlemn.ro", MARIA));

        ViewPeopleResult result = service.execute();

        assertThat(result.invitations().orElseThrow()).hasSize(2);
    }

    @Test
    void theOnlyMemberIsToldSoRatherThanShownATreeOfOneNode() {
        callerIs(MARIA_PERSON);
        when(loadMembershipPort.listAll())
                .thenReturn(List.of(membership(MARIA, MARIA_PERSON, null, MembershipStatus.ACTIVE)));
        when(describePeoplePort.describe(any()))
                .thenReturn(List.of(new DescribePeoplePort.PersonDescription(MARIA_PERSON, "Maria Ionescu", "OWNER")));
        seesNoInvitations();

        ViewPeopleResult result = service.execute();

        assertThat(result.onlyMember()).isTrue();
        assertThat(result.people()).hasSize(1);
    }

    @Test
    void aWorkspaceWithColleaguesIsNotReportedAsOnlyTheOwner() {
        callerIs(MARIA_PERSON);
        wholeWorkspaceLoaded();
        seesNoInvitations();

        assertThat(service.execute().onlyMember()).isFalse();
    }

    @Test
    void aCallerWithNoMembershipIsRefusedRatherThanShownAnEmptyDirectory() {
        when(identifyCallerPort.currentCaller())
                .thenReturn(Optional.of(new IdentifyCallerPort.Caller(MARIA_PERSON, true, false, "WORKSPACE_SETUP")));
        when(loadMembershipPort.findByPerson(MARIA_PERSON)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute()).isInstanceOf(SetupNotPermittedException.class);

        verify(loadMembershipPort, never()).listAll();
    }

    @Test
    void aCallerWhoseMembershipIsDeactivatedIsRefusedTheDirectory() {
        when(identifyCallerPort.currentCaller())
                .thenReturn(Optional.of(new IdentifyCallerPort.Caller(ANDREI_PERSON, false, true, "WORKSPACE")));
        when(loadMembershipPort.findByPerson(ANDREI_PERSON))
                .thenReturn(Optional.of(new Membership(ANDREI, ANDREI_PERSON, MembershipStatus.DEACTIVATED, MARIA)));

        assertThatThrownBy(() -> service.execute()).isInstanceOf(SetupNotPermittedException.class);

        verify(loadMembershipPort, never()).listAll();
    }

    @Test
    void aCallWithNoSessionBehindItIsRefused() {
        when(identifyCallerPort.currentCaller()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute()).isInstanceOf(NotAuthenticatedException.class);

        verify(loadMembershipPort, never()).listAll();
    }

    @Test
    void aDirectoryRowCarriesNoAddressAndNoMeasureOfAnybodysWork() {
        List<String> forbidden =
                List.of("email", "address", "count", "score", "average", "rank", "rating", "total", "percent");

        List<String> components = java.util.Arrays.stream(ViewPeopleResult.Person.class.getRecordComponents())
                .map(RecordComponent::getName)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .toList();

        assertThat(components)
                .as("a directory row may carry no address and no measure of work")
                .noneSatisfy(component -> assertThat(forbidden)
                        .anySatisfy(word -> assertThat(component).contains(word)));
    }

    private void callerIs(PersonId person) {
        when(identifyCallerPort.currentCaller())
                .thenReturn(Optional.of(new IdentifyCallerPort.Caller(person, false, true, "WORKSPACE")));
        lenient()
                .when(loadMembershipPort.findByPerson(person))
                .thenReturn(Optional.of(
                        new Membership(MembershipId.of(UUID.randomUUID()), person, MembershipStatus.ACTIVE, null)));
    }

    private void wholeWorkspaceLoaded() {
        when(loadMembershipPort.listAll())
                .thenReturn(List.of(
                        membership(MARIA, MARIA_PERSON, null, MembershipStatus.ACTIVE),
                        membership(IONUT, IONUT_PERSON, MARIA, MembershipStatus.ACTIVE),
                        membership(IOANA, IOANA_PERSON, IONUT, MembershipStatus.ACTIVE),
                        new Membership(ANDREI, ANDREI_PERSON, MembershipStatus.DEACTIVATED, MARIA, DEACTIVATED_ON)));

        when(describePeoplePort.describe(any()))
                .thenReturn(List.of(
                        new DescribePeoplePort.PersonDescription(MARIA_PERSON, "Maria Ionescu", "OWNER"),
                        new DescribePeoplePort.PersonDescription(IONUT_PERSON, "Ionuț Petrescu", "MANAGER"),
                        new DescribePeoplePort.PersonDescription(IOANA_PERSON, "Ioana Radu", "EMPLOYEE"),
                        new DescribePeoplePort.PersonDescription(ANDREI_PERSON, "Andrei Munteanu", "EMPLOYEE")));
    }

    private void seesNoInvitations() {
        lenient().when(callerPermissionsPort.callerHolds(anyString())).thenReturn(false);
        lenient().when(loadInvitationPort.listOpen()).thenReturn(List.of());
    }

    private void seesInvitations(Invitation... invitations) {
        when(callerPermissionsPort.callerHolds("PERSON_INVITE")).thenReturn(true);
        when(loadInvitationPort.listOpen()).thenReturn(List.of(invitations));
    }

    private static Membership membership(
            MembershipId id, PersonId person, MembershipId manager, MembershipStatus status) {
        return new Membership(id, person, status, manager);
    }

    private static Invitation openInvitationTo(String email, MembershipId inviter) {
        return new Invitation(
                InvitationId.of(UUID.randomUUID()),
                WORKSPACE,
                new EmailAddress(email),
                InvitedRole.EMPLOYEE,
                IONUT,
                inviter,
                InvitationState.SENT,
                "irrelevant-hash",
                NOW.plusSeconds(604800),
                NOW,
                null);
    }

    private static ViewPeopleResult.Person personNamed(ViewPeopleResult result, String displayName) {
        return result.people().stream()
                .filter(person -> person.displayName().equals(displayName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no person named " + displayName + " in the directory"));
    }
}
