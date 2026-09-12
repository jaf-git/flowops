package com.flowops.workspace.application.shared.port;

import com.flowops.workspace.domain.model.EmailAddress;
import com.flowops.workspace.domain.model.Membership;
import com.flowops.workspace.domain.model.MembershipId;
import com.flowops.workspace.domain.model.PersonId;
import java.util.List;
import java.util.Optional;

public interface LoadMembershipPort {
    Optional<Membership> findByPerson(PersonId person);

    Optional<Membership> findById(MembershipId id);

    Optional<Membership> findByEmail(EmailAddress email);

    List<Membership> listAll();
}
