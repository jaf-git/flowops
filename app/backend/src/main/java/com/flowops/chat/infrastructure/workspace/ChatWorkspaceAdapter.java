package com.flowops.chat.infrastructure.workspace;

import com.flowops.chat.application.shared.port.ChatDirectoryPort;
import com.flowops.chat.application.shared.port.TeamMembershipPort;
import com.flowops.workspace.application.describedirectory.DescribeDirectoryUseCase;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ChatWorkspaceAdapter implements ChatDirectoryPort, TeamMembershipPort {
    private final DescribeDirectoryUseCase directory;

    public ChatWorkspaceAdapter(DescribeDirectoryUseCase directory) {
        this.directory = directory;
    }

    @Override
    public UUID currentWorkspaceId() {
        return directory.currentWorkspaceId();
    }

    @Override
    public Optional<Person> describe(UUID userId) {
        return directory.describe(userId).map(ChatWorkspaceAdapter::translated);
    }

    @Override
    public List<Person> describeAll(Collection<UUID> userIds) {
        return directory.describeAll(userIds).stream()
                .map(ChatWorkspaceAdapter::translated)
                .toList();
    }

    @Override
    public Set<UUID> directReportsOf(UUID managerUserId) {
        return directory.directReportsOf(managerUserId);
    }

    @Override
    public Set<UUID> teamOf(UUID managerUserId) {
        Set<UUID> team = new LinkedHashSet<>(directory.directReportsOf(managerUserId));
        team.add(managerUserId);
        return Set.copyOf(team);
    }

    private static Person translated(DescribeDirectoryUseCase.Person person) {
        return new Person(person.userId(), person.displayName(), person.active());
    }
}
