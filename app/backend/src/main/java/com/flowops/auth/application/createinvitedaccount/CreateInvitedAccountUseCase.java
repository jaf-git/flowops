package com.flowops.auth.application.createinvitedaccount;

public interface CreateInvitedAccountUseCase {
    InvitedAccountCreated execute(CreateInvitedAccountCommand command);
}
