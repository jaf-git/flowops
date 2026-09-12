package com.flowops.auth.application.shared.port;

import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.auth.domain.model.User;
import com.flowops.auth.domain.model.UserId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LoadUserPort {
    Optional<User> loadByEmail(EmailAddress email);

    Optional<User> loadById(UserId userId);

    List<User> loadAllById(Collection<UserId> userIds);

    boolean existsByEmail(EmailAddress email);

    boolean anOwnerExists();
}
