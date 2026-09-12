package com.flowops.auth.application.describepeople;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DescribePeopleUseCase {
    List<PersonDescription> execute(Collection<UUID> userIds);

    record PersonDescription(UUID userId, String displayName, String role) {}
}
