package com.flowops.nodepipeline.infrastructure.persistence;

import com.flowops.nodepipeline.application.port.PipelineAudiencePort;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PipelineAudienceAdapter implements PipelineAudiencePort {
    private final JdbcTemplate jdbc;

    public PipelineAudienceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UUID workspace() {
        return jdbc.queryForObject("select id from workspace order by id limit 1", UUID.class);
    }

    @Override
    public Optional<UUID> workspaceOwner() {
        return jdbc
                .query(
                        """
                        select u.id
                          from auth_user u
                          join workspace_membership m on m.user_id = u.id
                         where u.role_name = 'OWNER'
                           and m.deactivated_at is null
                         order by u.id
                         limit 1
                        """,
                        (ResultSet row, int index) -> row.getObject("id", UUID.class))
                .stream()
                .findFirst();
    }

    @Override
    public Map<String, UUID> openedBy(Collection<String> jobIds) {
        Map<String, UUID> owners = new LinkedHashMap<>();
        UUID[] ids = asUuids(jobIds);
        if (ids.length == 0) {
            return owners;
        }
        jdbc.query(
                """
                select j.id, j.opened_by
                  from job j
                 where j.id = any(?)
                """,
                (ResultSet row) -> {
                    owners.put(row.getObject("id", UUID.class).toString(), row.getObject("opened_by", UUID.class));
                },
                (Object) ids);
        return owners;
    }

    @Override
    public Map<String, UUID> markedBy(Collection<String> nodeIds) {
        Map<String, UUID> markers = new LinkedHashMap<>();
        UUID[] ids = asUuids(nodeIds);
        if (ids.length == 0) {
            return markers;
        }
        jdbc.query(
                """
                select n.id, coalesce(n.marker_id, n.creator_id) as address
                  from work_node n
                 where n.id = any(?)
                """,
                (ResultSet row) -> {
                    markers.put(row.getObject("id", UUID.class).toString(), row.getObject("address", UUID.class));
                },
                (Object) ids);
        return markers;
    }

    @Override
    public Set<UUID> stillHere(Collection<UUID> people) {
        if (people.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.query(
                """
                select m.user_id
                  from workspace_membership m
                 where m.deactivated_at is null
                   and m.user_id = any(?)
                """,
                (ResultSet row, int index) -> row.getObject("user_id", UUID.class),
                (Object) people.toArray(UUID[]::new)));
    }

    @Override
    public Set<String> alreadyNudged(Collection<String> nodeIds) {
        UUID[] ids = asUuids(nodeIds);
        if (ids.length == 0) {
            return Set.of();
        }
        return new HashSet<>(jdbc.query(
                """
                select n.id
                  from work_node n
                 where n.id = any(?)
                   and n.nudged_at is not null
                """,
                (ResultSet row, int index) -> row.getObject("id", UUID.class).toString(),
                (Object) ids));
    }

    private static UUID[] asUuids(Collection<String> ids) {
        List<UUID> parsed = ids.stream()
                .map(PipelineAudienceAdapter::uuidOrNull)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        return parsed.toArray(UUID[]::new);
    }

    private static UUID uuidOrNull(String id) {
        try {
            return id == null ? null : UUID.fromString(id);
        } catch (IllegalArgumentException notAnIdentifier) {
            return null;
        }
    }
}
