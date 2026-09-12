package com.flowops.workspace.application.eraseperson;

import com.flowops.workspace.domain.model.MembershipId;

public record ErasePersonCommand(MembershipId person, String typedName) {}
