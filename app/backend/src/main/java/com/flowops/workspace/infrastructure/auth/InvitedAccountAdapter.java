package com.flowops.workspace.infrastructure.auth;

import com.flowops.auth.application.createinvitedaccount.CreateInvitedAccountCommand;
import com.flowops.auth.application.createinvitedaccount.CreateInvitedAccountUseCase;
import com.flowops.auth.application.createinvitedaccount.InvitedAccountCreated;
import com.flowops.auth.application.createinvitedaccount.InvitedAddressTakenException;
import com.flowops.auth.application.createinvitedaccount.InvitedNameRequiredException;
import com.flowops.auth.application.createinvitedaccount.InvitedPasswordRefusedException;
import com.flowops.auth.application.shared.ClientContext;
import com.flowops.workspace.application.shared.exception.AlreadyMemberException;
import com.flowops.workspace.application.shared.exception.MemberNameRequiredException;
import com.flowops.workspace.application.shared.exception.PasswordUnacceptableException;
import com.flowops.workspace.application.shared.port.CreateInvitedAccountPort;
import com.flowops.workspace.domain.model.EmailAddress;
import com.flowops.workspace.domain.model.PersonId;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class InvitedAccountAdapter implements CreateInvitedAccountPort {
    private final CreateInvitedAccountUseCase createInvitedAccount;
    private final HttpServletRequest request;

    public InvitedAccountAdapter(CreateInvitedAccountUseCase createInvitedAccount, HttpServletRequest request) {
        this.createInvitedAccount = createInvitedAccount;
        this.request = request;
    }

    @Override
    public InvitedAccount create(EmailAddress email, String displayName, String role, String password) {
        InvitedAccountCreated created;
        try {
            created = createInvitedAccount.execute(
                    new CreateInvitedAccountCommand(email.value(), displayName, role, password, clientContext()));
        } catch (InvitedPasswordRefusedException refused) {
            throw new PasswordUnacceptableException(refused.rules());
        } catch (InvitedNameRequiredException refused) {
            throw new MemberNameRequiredException();
        } catch (InvitedAddressTakenException refused) {
            throw new AlreadyMemberException("you already have an account here; sign in instead");
        }
        return new InvitedAccount(
                PersonId.of(created.userId()),
                created.email(),
                created.accountState(),
                created.permissions(),
                created.landingTarget());
    }

    private ClientContext clientContext() {
        return new ClientContext(request.getRemoteAddr(), request.getHeader("User-Agent"));
    }
}
