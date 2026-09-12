package com.flowops.discovery.infrastructure.persistence;

import com.flowops.discovery.application.clustering.DiscoveredWorkPort;
import com.flowops.discovery.application.digest.DigestReadPort;
import com.flowops.discovery.domain.model.JobId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DiscoveredWorkReadAdapter implements DiscoveredWorkPort, DigestReadPort {
    private final JdbcTemplate jdbc;

    public DiscoveredWorkReadAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String ENGAGEMENTS_WITH_CLOSED_WORK =
            """
            select distinct job_id
            from track
            where closed_at is not null
            order by job_id
            """;

    @Override
    public List<JobId> engagementsWithClosedWork() {
        return jdbc.query(ENGAGEMENTS_WITH_CLOSED_WORK, (row, index) -> JobId.of(row.getObject("job_id", UUID.class)));
    }

    private static final String STATED_JOB_NAMES = "select id, name from functional_role";

    @Override
    public Map<UUID, String> statedJobNames() {
        Map<UUID, String> names = new HashMap<>();
        jdbc.query(STATED_JOB_NAMES, row -> {
            names.put(row.getObject("id", UUID.class), row.getString("name"));
        });
        return names;
    }

    private static final String THREADS_CLOSED_SINCE = "select count(*) from track where closed_at >= ?";

    @Override
    public int threadsClosedSince(Instant since) {
        Integer closed = jdbc.queryForObject(THREADS_CLOSED_SINCE, Integer.class, Timestamp.from(since));
        return closed == null ? 0 : closed;
    }

    private static final String WORK_MILLIS_BY_TYPE =
            """
            select t.track_type_id as type_id,
                   coalesce(sum(extract(epoch from (p.ended_at - p.started_at)) * 1000), 0)::bigint as work_ms
            from track t
                     join work_node n on n.track_id = t.id
                     join node_phase_row p on p.work_node_id = n.id
            where t.track_type_id is not null
              and p.phase = 'WORK'
              and p.ended_at is not null
            group by t.track_type_id
            """;

    @Override
    public Map<UUID, Long> workMillisByType() {
        Map<UUID, Long> byType = new HashMap<>();
        jdbc.query(WORK_MILLIS_BY_TYPE, row -> {
            byType.put(row.getObject("type_id", UUID.class), row.getLong("work_ms"));
        });
        return byType;
    }

    private static final String WORK_MILLIS_BY_PERFORMER_ROLE =
            """
            select n.performer_role_id as role_id,
                   coalesce(sum(extract(epoch from (p.ended_at - p.started_at)) * 1000), 0)::bigint as work_ms
            from work_node n
                     join node_phase_row p on p.work_node_id = n.id
            where n.performer_role_id is not null
              and n.created_at >= ?
              and p.phase = 'WORK'
              and p.ended_at is not null
            group by n.performer_role_id
            """;

    @Override
    public Map<UUID, Long> workMillisByPerformerRoleSince(Instant since) {
        Map<UUID, Long> byRole = new HashMap<>();
        jdbc.query(
                WORK_MILLIS_BY_PERFORMER_ROLE,
                row -> {
                    byRole.put(row.getObject("role_id", UUID.class), row.getLong("work_ms"));
                },
                Timestamp.from(since));
        return byRole;
    }

    private static final String ROLE_ACTIVITY_SINCE =
            """
            select r.id                                          as role_id,
                   r.name                                        as role_name,
                   count(distinct m.user_id)                     as people_holding_it,
                   count(distinct w.creator_id)                  as people_who_marked_work
            from functional_role r
                     join workspace_membership m
                          on m.functional_role_id = r.id and m.status = 'ACTIVE'
                     left join work_node w
                          on w.creator_id = m.user_id and w.created_at >= ?
            group by r.id, r.name
            order by r.name
            """;

    @Override
    public List<RoleActivity> roleActivitySince(Instant since) {
        return jdbc.query(
                ROLE_ACTIVITY_SINCE,
                (row, index) -> new RoleActivity(
                        row.getObject("role_id", UUID.class),
                        row.getString("role_name"),
                        row.getInt("people_holding_it"),
                        row.getInt("people_who_marked_work")),
                Timestamp.from(since));
    }
}
