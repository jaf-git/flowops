package com.flowops.workspace.application.shared.port;

import com.flowops.workspace.domain.model.EmailAddress;
import com.flowops.workspace.domain.model.Invitation;
import com.flowops.workspace.domain.model.InvitationId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LoadInvitationPort {
    Optional<Invitation> findByTokenHash(String tokenHash);

    Optional<Invitation> findOpenFor(EmailAddress email);

    Optional<Instant> findLastDeclineFor(EmailAddress email);

    List<Invitation> listOpen();

    Optional<Invitation> findByIdForUpdate(InvitationId id);

    Optional<Invitation> findByTokenHashForUpdate(String tokenHash);
}
