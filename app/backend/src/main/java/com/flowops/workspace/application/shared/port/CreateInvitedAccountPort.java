package com.flowops.workspace.application.shared.port;

import com.flowops.workspace.domain.model.EmailAddress;
import com.flowops.workspace.domain.model.PersonId;
import java.util.Set;

public interface CreateInvitedAccountPort {
    InvitedAccount create(EmailAddress email, String displayName, String role, String password);

    record InvitedAccount(
            PersonId person, String email, String accountState, Set<String> permissions, String landingTarget) {}
}
