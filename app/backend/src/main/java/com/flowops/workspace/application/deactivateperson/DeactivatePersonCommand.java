package com.flowops.workspace.application.deactivateperson;

import com.flowops.workspace.domain.model.MembershipId;

public record DeactivatePersonCommand(MembershipId person) {}
