package com.flowops.workspace.infrastructure.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.flowops.auth.application.createinvitedaccount.CreateInvitedAccountUseCase;
import com.flowops.auth.application.createinvitedaccount.InvitedAddressTakenException;
import com.flowops.auth.application.createinvitedaccount.InvitedNameRequiredException;
import com.flowops.auth.application.createinvitedaccount.InvitedPasswordRefusedException;
import com.flowops.workspace.application.shared.exception.AlreadyMemberException;
import com.flowops.workspace.application.shared.exception.MemberNameRequiredException;
import com.flowops.workspace.application.shared.exception.PasswordUnacceptableException;
import com.flowops.workspace.domain.model.EmailAddress;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;

@Tag("AUTH-ACCEPT-INVITE-01")
class InvitedAccountAdapterTest {
    private CreateInvitedAccountUseCase useCase;
    private InvitedAccountAdapter adapter;

    @BeforeEach
    void buildTheAdapterOverADoubledUseCase() {
        useCase = Mockito.mock(CreateInvitedAccountUseCase.class);
        HttpServletRequest request = new MockHttpServletRequest();
        adapter = new InvitedAccountAdapter(useCase, request);
    }

    @Test
    void aPasswordRefusalCrossesTheBoundaryCarryingTheRulesThatWereMissed() {
        when(useCase.execute(any())).thenThrow(new InvitedPasswordRefusedException(List.of("MINIMUM_LENGTH")));

        assertThatThrownBy(() -> create("Cosmin Ionescu", "short"))
                .isInstanceOf(PasswordUnacceptableException.class)
                .extracting(failure -> ((PasswordUnacceptableException) failure).rules())
                .isEqualTo(List.of("MINIMUM_LENGTH"));
    }

    @Test
    void anEmptyNameCrossesTheBoundaryAsTheInvitationScreensOwnRefusal() {
        when(useCase.execute(any())).thenThrow(new InvitedNameRequiredException());

        assertThatThrownBy(() -> create("   ", "a-long-enough-passphrase"))
                .isInstanceOf(MemberNameRequiredException.class);
    }

    @Test
    void anAddressThatAlreadyHasAnAccountCrossesTheBoundaryAsAlreadyMember() {
        when(useCase.execute(any())).thenThrow(new InvitedAddressTakenException());

        assertThatThrownBy(() -> create("Cosmin Ionescu", "a-long-enough-passphrase"))
                .isInstanceOf(AlreadyMemberException.class);
    }

    @Test
    void anUnrecognisedFailureIsNotDressedUpAsSomethingThePersonCouldActOn() {
        when(useCase.execute(any())).thenThrow(new IllegalStateException("something else entirely"));

        assertThatThrownBy(() -> create("Cosmin Ionescu", "a-long-enough-passphrase"))
                .isInstanceOf(IllegalStateException.class);
    }

    private void create(String displayName, String password) {
        adapter.create(EmailAddress.of("cosmin.ionescu@atelier.ro"), displayName, "EMPLOYEE", password);
    }
}
