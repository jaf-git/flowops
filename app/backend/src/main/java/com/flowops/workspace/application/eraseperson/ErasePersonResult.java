package com.flowops.workspace.application.eraseperson;

import com.flowops.workspace.domain.model.MembershipId;
import com.flowops.workspace.domain.model.PersonId;

public record ErasePersonResult(MembershipId person, PersonId opaqueIdentifier, boolean changed) {}
