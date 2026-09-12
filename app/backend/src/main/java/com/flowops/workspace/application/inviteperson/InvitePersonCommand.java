package com.flowops.workspace.application.inviteperson;

import com.flowops.workspace.domain.enums.InvitedRole;
import com.flowops.workspace.domain.model.EmailAddress;
import com.flowops.workspace.domain.model.MembershipId;
import java.util.Objects;

public record InvitePersonCommand(EmailAddress email, InvitedRole intendedRole, MembershipId intendedManager) {
    public InvitePersonCommand {
        Objects.requireNonNull(email, "an address is required");
        Objects.requireNonNull(intendedRole, "a role is required");
        Objects.requireNonNull(intendedManager, "a reporting line is required");
    }
}
