package com.flowops.workspace.application.describedirectory;

import com.flowops.workspace.application.shared.port.DescribePeoplePort;
import com.flowops.workspace.application.shared.port.LoadMembershipPort;
import com.flowops.workspace.application.shared.port.LoadWorkspacePort;
import com.flowops.workspace.domain.model.Membership;
import com.flowops.workspace.domain.model.MembershipId;
import com.flowops.workspace.domain.model.PersonId;
import com.flowops.workspace.domain.model.ReportingTree;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DescribeDirectoryService implements DescribeDirectoryUseCase {
    private final LoadMembershipPort loadMembershipPort;
    private final DescribePeoplePort describePeoplePort;
    private final LoadWorkspacePort loadWorkspacePort;

    public DescribeDirectoryService(
            LoadMembershipPort loadMembershipPort,
            DescribePeoplePort describePeoplePort,
            LoadWorkspacePort loadWorkspacePort) {
        this.loadMembershipPort = loadMembershipPort;
        this.describePeoplePort = describePeoplePort;
        this.loadWorkspacePort = loadWorkspacePort;
    }

    @Override
    @Transactional(readOnly = true)
    public UUID currentWorkspaceId() {
        return loadWorkspacePort.load().id().value();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Person> describe(UUID userId) {
        return describeAll(List.of(userId)).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Person> describeAll(Collection<UUID> userIds) {
        Set<PersonId> wanted = userIds.stream().map(PersonId::of).collect(Collectors.toCollection(LinkedHashSet::new));
        if (wanted.isEmpty()) {
            return List.of();
        }

        Map<PersonId, Membership> byPerson = loadMembershipPort.listAll().stream()
                .filter(membership -> wanted.contains(membership.person()))
                .filter(membership -> !membership.isErased())
                .collect(Collectors.toMap(Membership::person, Function.identity(), (first, second) -> first));

        Map<PersonId, String> names = describePeoplePort.describe(byPerson.keySet()).stream()
                .collect(Collectors.toMap(
                        DescribePeoplePort.PersonDescription::id, DescribePeoplePort.PersonDescription::displayName));

        return wanted.stream()
                .filter(byPerson::containsKey)
                .map(person -> new Person(
                        person.value(),
                        names.getOrDefault(person, ""),
                        byPerson.get(person).isActive()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isWithinScopeOf(UUID actorUserId, UUID subjectUserId) {
        if (actorUserId.equals(subjectUserId)) {
            return true;
        }

        List<Membership> memberships = loadMembershipPort.listAll();
        Optional<Membership> actor = findPerson(memberships, actorUserId);
        if (actor.isEmpty() || findPerson(memberships, subjectUserId).isEmpty()) {
            return false;
        }

        if (actor.get().managerOrRoot().isEmpty()) {
            return true;
        }

        return subtreeIdentifiersOf(memberships, actor.get()).contains(subjectUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> subtreeOf(UUID actorUserId) {
        List<Membership> memberships = loadMembershipPort.listAll();
        return findPerson(memberships, actorUserId)
                .map(actor -> subtreeIdentifiersOf(memberships, actor))
                .orElseGet(Set::of);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> directReportsOf(UUID managerUserId) {
        List<Membership> memberships = loadMembershipPort.listAll();
        return findPerson(memberships, managerUserId)
                .map(manager -> memberships.stream()
                        .filter(membership -> !membership.isErased())
                        .filter(membership -> membership
                                .managerOrRoot()
                                .map(theirManager -> theirManager.equals(manager.id()))
                                .orElse(false))
                        .map(membership -> membership.person().value())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()))
                .orElseGet(Set::of);
    }

    private Optional<Membership> findPerson(List<Membership> memberships, UUID userId) {
        return memberships.stream()
                .filter(membership -> membership.person().equals(PersonId.of(userId)))
                .filter(membership -> !membership.isErased())
                .findFirst();
    }

    private Set<UUID> subtreeIdentifiersOf(List<Membership> memberships, Membership actor) {
        ReportingTree tree = ReportingTree.of(memberships, Map.<MembershipId, String>of());
        return tree.subtreeOf(actor.id()).stream()
                .map(membership -> membership.person().value())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
