package com.flowops.workspace.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.workspace.domain.enums.MembershipStatus;
import com.flowops.workspace.domain.exception.CycleWouldFormException;
import com.flowops.workspace.domain.exception.ManagerNotEligibleException;
import com.flowops.workspace.domain.exception.OwnerHasNoManagerException;
import com.flowops.workspace.domain.exception.SelfManagerException;
import com.flowops.workspace.domain.exception.SubjectInactiveException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("WORKSPACE-EDIT-REPORTING-LINE-01")
class ReportingTreeTest {
    private static final MembershipId MARIA = MembershipId.of(UUID.randomUUID());
    private static final MembershipId IONUT = MembershipId.of(UUID.randomUUID());
    private static final MembershipId IOANA = MembershipId.of(UUID.randomUUID());
    private static final MembershipId ANDREI = MembershipId.of(UUID.randomUUID());
    private static final MembershipId ELENA = MembershipId.of(UUID.randomUUID());
    private static final MembershipId STEFAN = MembershipId.of(UUID.randomUUID());

    private final ReportingTree tree = ReportingTree.of(
            List.of(
                    membership(MARIA, null, MembershipStatus.ACTIVE),
                    membership(IONUT, MARIA, MembershipStatus.ACTIVE),
                    membership(IOANA, IONUT, MembershipStatus.ACTIVE),
                    membership(ANDREI, IOANA, MembershipStatus.ACTIVE),
                    membership(ELENA, MARIA, MembershipStatus.ACTIVE),
                    membership(STEFAN, MARIA, MembershipStatus.DEACTIVATED)),
            Map.of(
                    MARIA, "OWNER",
                    IONUT, "MANAGER",
                    IOANA, "MANAGER",
                    ANDREI, "EMPLOYEE",
                    ELENA, "EMPLOYEE",
                    STEFAN, "MANAGER"));

    @Test
    void aValidMoveReturnsTheMembershipCarryingItsNewManager() {
        Membership moved = tree.reassign(ELENA, IONUT).orElseThrow();

        assertThat(moved.id()).isEqualTo(ELENA);
        assertThat(moved.manager()).isEqualTo(IONUT);
    }

    @Test
    void theOwnerCannotBeGivenAManager() {
        assertThatThrownBy(() -> tree.reassign(MARIA, IONUT)).isInstanceOf(OwnerHasNoManagerException.class);
    }

    @Test
    void nobodyCanReportToThemselves() {
        assertThatThrownBy(() -> tree.reassign(IOANA, IOANA)).isInstanceOf(SelfManagerException.class);
    }

    @Test
    void aManagerCannotBeMovedUnderTheirOwnDirectReport() {
        assertThatThrownBy(() -> tree.reassign(IONUT, IOANA))
                .isInstanceOf(CycleWouldFormException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(CycleWouldFormException.class))
                .satisfies(cycle -> assertThat(cycle.path()).containsExactly(IONUT, IOANA));
    }

    @Test
    void aManagerCannotBeMovedUnderSomebodySeveralLevelsBelowThem() {
        assertThatThrownBy(() -> tree.reassign(IONUT, ANDREI))
                .isInstanceOf(CycleWouldFormException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(CycleWouldFormException.class))
                .satisfies(cycle -> assertThat(cycle.path())
                        .as("the path names every step that closes the loop")
                        .containsExactly(IONUT, IOANA, ANDREI));
    }

    @Test
    void aDeactivatedPersonCannotBeGivenReports() {
        assertThatThrownBy(() -> tree.reassign(ELENA, STEFAN))
                .isInstanceOf(ManagerNotEligibleException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(ManagerNotEligibleException.class))
                .satisfies(refusal -> assertThat(refusal.code()).isEqualTo("MANAGER_INACTIVE"));
    }

    @Test
    void anEmployeeCannotBeGivenReports() {
        assertThatThrownBy(() -> tree.reassign(ELENA, ANDREI))
                .isInstanceOf(ManagerNotEligibleException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(ManagerNotEligibleException.class))
                .satisfies(refusal -> assertThat(refusal.code()).isEqualTo("MANAGER_NOT_ELIGIBLE"));
    }

    @Test
    void somebodyWhoseAccessEndedCannotBeMoved() {
        assertThatThrownBy(() -> tree.reassign(STEFAN, IONUT)).isInstanceOf(SubjectInactiveException.class);
    }

    @Test
    void thePersonBeingInactiveIsAnsweredBeforeAnythingAboutTheProposal() {
        assertThatThrownBy(() -> tree.reassign(STEFAN, STEFAN)).isInstanceOf(SubjectInactiveException.class);
        assertThatThrownBy(() -> tree.reassign(STEFAN, MARIA)).isInstanceOf(SubjectInactiveException.class);
    }

    @Test
    void proposingTheManagerSomebodyAlreadyHasChangesNothing() {
        assertThat(tree.reassign(IOANA, IONUT)).isEmpty();
    }

    @Test
    void everybodyBelowAPersonMovesWithThem() {
        assertThat(tree.subtreeOf(IONUT)).extracting(Membership::id).containsExactly(IOANA, ANDREI);
    }

    @Test
    void somebodyWithNoReportsMovesAlone() {
        assertThat(tree.subtreeOf(ANDREI)).isEmpty();
    }

    @Test
    void thePersonBeingMovedIsNotCountedAmongThoseMovingWithThem() {
        assertThat(tree.subtreeOf(IONUT)).extracting(Membership::id).doesNotContain(IONUT);
    }

    @Test
    void movingSomebodyChangesTheirEdgeAndNothingElseAboutThem() {
        Membership before = tree.find(ELENA).orElseThrow();
        Membership after = tree.reassign(ELENA, IONUT).orElseThrow();

        assertThat(after.person()).isEqualTo(before.person());
        assertThat(after.status()).isEqualTo(before.status());
        assertThat(after.deactivatedAt()).isEqualTo(before.deactivatedAt());
    }

    private static Membership membership(MembershipId id, MembershipId manager, MembershipStatus status) {
        return new Membership(id, PersonId.of(UUID.randomUUID()), status, manager, null);
    }
}
