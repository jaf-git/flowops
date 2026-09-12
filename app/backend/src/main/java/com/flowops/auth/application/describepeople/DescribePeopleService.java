package com.flowops.auth.application.describepeople;

import com.flowops.auth.application.shared.port.LoadUserPort;
import com.flowops.auth.domain.model.User;
import com.flowops.auth.domain.model.UserId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DescribePeopleService implements DescribePeopleUseCase {
    private final LoadUserPort loadUserPort;

    public DescribePeopleService(LoadUserPort loadUserPort) {
        this.loadUserPort = loadUserPort;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PersonDescription> execute(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        return loadUserPort.loadAllById(userIds.stream().map(UserId::of).toList()).stream()
                .flatMap(user -> describe(user).stream())
                .toList();
    }

    private java.util.Optional<PersonDescription> describe(User user) {
        return user.displayName()
                .map(name -> new PersonDescription(
                        user.id().value(), name.value(), user.role().value()));
    }
}
