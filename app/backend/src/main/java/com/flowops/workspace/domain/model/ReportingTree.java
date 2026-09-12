package com.flowops.workspace.domain.model;

import com.flowops.workspace.domain.enums.MembershipStatus;
import com.flowops.workspace.domain.exception.CycleWouldFormException;
import com.flowops.workspace.domain.exception.ManagerNotEligibleException;
import com.flowops.workspace.domain.exception.OwnerHasNoManagerException;
import com.flowops.workspace.domain.exception.SelfManagerException;
import com.flowops.workspace.domain.exception.SubjectInactiveException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ReportingTree {
    private static final List<String> MAY_HOLD_REPORTS = List.of("OWNER", "MANAGER");

    private final Map<MembershipId, Membership> byId;
    private final Map<MembershipId, String> roles;

    private ReportingTree(Map<MembershipId, Membership> byId, Map<MembershipId, String> roles) {
        this.byId = byId;
        this.roles = roles;
    }

    public static ReportingTree of(List<Membership> memberships, Map<MembershipId, String> roles) {
        Map<MembershipId, Membership> byId = new LinkedHashMap<>();
        for (Membership membership : memberships) {
            byId.put(membership.id(), membership);
        }
        return new ReportingTree(byId, Map.copyOf(roles));
    }

    public Optional<Membership> find(MembershipId id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<Membership> reassign(MembershipId person, MembershipId proposedManager) {
        Membership subject = required(person);
        Membership manager = required(proposedManager);

        if (subject.managerOrRoot().isEmpty()) {
            throw new OwnerHasNoManagerException();
        }
        if (!subject.isActive()) {
            throw new SubjectInactiveException();
        }

        if (person.equals(proposedManager)) {
            throw new SelfManagerException();
        }

        if (proposedManager.equals(subject.manager())) {
            return Optional.empty();
        }

        List<MembershipId> path = pathFromRoot(proposedManager);
        if (path.contains(person)) {
            throw new CycleWouldFormException(cyclePath(person, path));
        }

        if (!manager.isActive()) {
            throw new ManagerNotEligibleException("MANAGER_INACTIVE", "that person is no longer active");
        }
        if (!MAY_HOLD_REPORTS.contains(roles.getOrDefault(proposedManager, ""))) {
            throw new ManagerNotEligibleException(
                    "MANAGER_NOT_ELIGIBLE", "reports attach only to somebody holding the owner or manager role");
        }

        return Optional.of(new Membership(
                subject.id(), subject.person(), subject.status(), proposedManager, subject.deactivatedAt()));
    }

    public Optional<Membership> firstActiveFrom(MembershipId proposed) {
        Membership at = byId.get(proposed);

        for (int step = 0; step <= byId.size() && at != null; step++) {
            if (at.status() == MembershipStatus.ACTIVE) {
                return Optional.of(at);
            }
            at = at.manager() == null ? null : byId.get(at.manager());
        }
        return Optional.empty();
    }

    public List<Membership> subtreeOf(MembershipId person) {
        Map<MembershipId, List<Membership>> children = new LinkedHashMap<>();
        for (Membership candidate : byId.values()) {
            MembershipId manager = candidate.manager();
            if (manager != null) {
                children.computeIfAbsent(manager, key -> new ArrayList<>()).add(candidate);
            }
        }

        List<Membership> moving = new ArrayList<>();
        List<MembershipId> frontier = new ArrayList<>(List.of(person));
        while (!frontier.isEmpty()) {
            List<MembershipId> next = new ArrayList<>();
            for (MembershipId at : frontier) {
                for (Membership child : children.getOrDefault(at, List.of())) {
                    if (!moving.contains(child)) {
                        moving.add(child);
                        next.add(child.id());
                    }
                }
            }
            frontier = next;
        }
        return List.copyOf(moving);
    }

    private List<MembershipId> pathFromRoot(MembershipId from) {
        List<MembershipId> upward = new ArrayList<>();
        MembershipId cursor = from;

        while (cursor != null && !upward.contains(cursor) && upward.size() <= byId.size()) {
            upward.add(cursor);
            Membership at = byId.get(cursor);
            cursor = at == null ? null : at.manager();
        }

        List<MembershipId> fromRoot = new ArrayList<>(upward);
        java.util.Collections.reverse(fromRoot);
        return List.copyOf(fromRoot);
    }

    private List<MembershipId> cyclePath(MembershipId person, List<MembershipId> fromRoot) {
        return List.copyOf(fromRoot.subList(fromRoot.indexOf(person), fromRoot.size()));
    }

    private Membership required(MembershipId id) {
        return Optional.ofNullable(byId.get(id)).orElseThrow(() -> new IllegalArgumentException("no membership " + id));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ReportingTree tree && byId.equals(tree.byId) && roles.equals(tree.roles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(byId, roles);
    }
}
