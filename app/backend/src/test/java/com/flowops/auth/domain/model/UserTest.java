package com.flowops.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.auth.domain.enums.AccountState;
import com.flowops.auth.domain.enums.LandingTarget;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserTest {
    private static final EmailAddress EMAIL = new EmailAddress("founder@flowops.test");
    private static final Instant CREATED_AT = Instant.parse("2026-08-02T09:00:00Z");

    @Test
    void aRegisteredOwnerIsActiveAndHoldsTheOwnerRole() {
        User user = User.registerOwner(EMAIL, CREATED_AT);

        assertThat(user.accountState()).isEqualTo(AccountState.ACTIVE);
        assertThat(user.role()).isEqualTo(RoleName.OWNER);
    }

    @Test
    void anActiveAccountMayAuthenticate() {
        User user = User.registerOwner(EMAIL, CREATED_AT);

        assertThat(user.canAuthenticate()).isTrue();
    }

    @Test
    void aDeactivatedAccountMayNotAuthenticate() {
        User user = User.rebuild(
                UserId.generate(), EMAIL, AccountState.DEACTIVATED, RoleName.EMPLOYEE, CREATED_AT, true, null);

        assertThat(user.canAuthenticate()).isFalse();
    }

    @Test
    void anInvitedAccountMayNotAuthenticateUntilItIsActivated() {
        User user =
                User.rebuild(UserId.generate(), EMAIL, AccountState.INVITED, RoleName.EMPLOYEE, CREATED_AT, true, null);

        assertThat(user.canAuthenticate()).isFalse();
    }

    @Test
    void anEmployeeLandsOnTheirOwnQueue() {
        User user =
                User.rebuild(UserId.generate(), EMAIL, AccountState.ACTIVE, RoleName.EMPLOYEE, CREATED_AT, true, null);

        assertThat(user.landingTarget()).isEqualTo(LandingTarget.MY_WORK);
    }

    @Test
    void aRegisteredOwnerHasNotSetUpTheWorkspaceYet() {
        User user = User.registerOwner(EMAIL, CREATED_AT);

        assertThat(user.setupCompleted()).isFalse();
    }

    @Test
    void anOwnerWhoHasNotSetUpTheWorkspaceLandsOnSetup() {
        User user = User.registerOwner(EMAIL, CREATED_AT);

        assertThat(user.landingTarget()).isEqualTo(LandingTarget.WORKSPACE_SETUP);
    }

    @Test
    void anOwnerWhoHasSetUpTheWorkspaceLandsOnSupervision() {
        User user = User.rebuild(UserId.generate(), EMAIL, AccountState.ACTIVE, RoleName.OWNER, CREATED_AT, true, null);

        assertThat(user.landingTarget()).isEqualTo(LandingTarget.TRIAGE);
    }

    @Test
    void theFlagRoutesOnlyTheOwner() {
        User manager =
                User.rebuild(UserId.generate(), EMAIL, AccountState.ACTIVE, RoleName.MANAGER, CREATED_AT, false, null);
        User employee =
                User.rebuild(UserId.generate(), EMAIL, AccountState.ACTIVE, RoleName.EMPLOYEE, CREATED_AT, false, null);

        assertThat(manager.landingTarget()).isEqualTo(LandingTarget.TRIAGE);
        assertThat(employee.landingTarget()).isEqualTo(LandingTarget.MY_WORK);
    }

    @Test
    void completingSetupFlipsTheFlagAndTheLandingTarget() {
        User owner = User.registerOwner(EMAIL, CREATED_AT);

        User afterSetup = owner.completeSetup();

        assertThat(afterSetup.setupCompleted()).isTrue();
        assertThat(afterSetup.landingTarget()).isEqualTo(LandingTarget.TRIAGE);
    }

    @Test
    void completingSetupChangesNothingElseAboutTheAccount() {
        User owner = User.registerOwner(EMAIL, CREATED_AT);

        User afterSetup = owner.completeSetup();

        assertThat(afterSetup.id()).isEqualTo(owner.id());
        assertThat(afterSetup.email()).isEqualTo(owner.email());
        assertThat(afterSetup.role()).isEqualTo(owner.role());
        assertThat(afterSetup.accountState()).isEqualTo(owner.accountState());
        assertThat(afterSetup.createdAt()).isEqualTo(owner.createdAt());
    }

    @Test
    void completingSetupTwiceIsHarmless() {
        User owner = User.registerOwner(EMAIL, CREATED_AT);

        assertThat(owner.completeSetup().completeSetup().setupCompleted()).isTrue();
    }

    @Test
    void whichRolesOwnWorkspaceSetupIsTheRoleNamesToSay() {
        assertThat(RoleName.OWNER.ownsWorkspaceSetup()).isTrue();
        assertThat(RoleName.MANAGER.ownsWorkspaceSetup()).isFalse();
        assertThat(RoleName.EMPLOYEE.ownsWorkspaceSetup()).isFalse();
        assertThat(new RoleName("COORDONATOR").ownsWorkspaceSetup()).isFalse();
    }
}
