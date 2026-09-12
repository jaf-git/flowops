package com.flowops.discovery.infrastructure.persistence;

import com.flowops.discovery.application.shared.port.ActivityPort;
import com.flowops.discovery.domain.enums.ActivityStatus;
import com.flowops.discovery.domain.model.Activity;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ActivityAdapter implements ActivityPort {
    private final JdbcTemplate jdbc;

    public ActivityAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UUID nextId() {
        return UUID.randomUUID();
    }

    @Override
    public void save(Activity activity) {
        jdbc.update(
                """
                insert into activity (id, name, slug, status, merged_into_id, times_used, last_used_at,
                                      created_by, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (id) do update set
                    name           = excluded.name,
                    slug           = excluded.slug,
                    status         = excluded.status,
                    merged_into_id = excluded.merged_into_id,
                    times_used     = excluded.times_used,
                    last_used_at   = excluded.last_used_at
                """,
                activity.id(),
                activity.name(),
                activity.slug(),
                activity.status().name(),
                activity.mergedIntoId().orElse(null),
                activity.timesUsed(),
                activity.lastUsedAt().map(Timestamp::from).orElse(null),
                activity.createdBy(),
                Timestamp.from(activity.createdAt()));
    }

    @Override
    public Optional<Activity> find(UUID id) {
        return jdbc.query("select * from activity where id = ?", ActivityAdapter::read, id).stream()
                .findFirst();
    }

    @Override
    public Optional<Activity> findBySlug(String slug) {
        return jdbc.query("select * from activity where slug = ?", ActivityAdapter::read, slug).stream()
                .findFirst();
    }

    @Override
    public List<Activity> all() {
        return jdbc.query("select * from activity order by times_used desc, lower(name)", ActivityAdapter::read);
    }

    @Override
    public List<Activity> choosable() {
        return jdbc.query(
                "select * from activity where status = 'ACTIVE' order by times_used desc, lower(name)",
                ActivityAdapter::read);
    }

    @Override
    public void nameTheWork(UUID workNodeId, UUID activityId, UUID by, Instant at) {
        jdbc.update("update work_node set activity_id = ? where id = ?", activityId, workNodeId);

        jdbc.update(
                """
                insert into activity_usage (work_node_id, activity_id, performer_role_id, counterparty_id,
                                            used_by, used_at)
                select n.id, ?, n.performer_role_id, j.counterparty_id, ?, ?
                from work_node n
                join job j on j.id = n.job_id
                where n.id = ?
                on conflict (work_node_id) do update set
                    activity_id       = excluded.activity_id,
                    performer_role_id = excluded.performer_role_id,
                    counterparty_id   = excluded.counterparty_id,
                    used_by           = excluded.used_by,
                    used_at           = excluded.used_at
                """,
                activityId,
                by,
                Timestamp.from(at),
                workNodeId);
    }

    @Override
    public long marksNaming(UUID activityId) {
        Long count =
                jdbc.queryForObject("select count(*) from work_node where activity_id = ?", Long.class, activityId);
        return count == null ? 0 : count;
    }

    @Override
    public Map<UUID, List<String>> departmentSpread() {
        Map<UUID, List<String>> spread = new LinkedHashMap<>();

        jdbc.query(
                """
                select distinct u.activity_id, r.name
                from activity_usage u
                join functional_role r on r.id = u.performer_role_id
                order by u.activity_id, r.name
                """,
                rs -> {
                    spread.computeIfAbsent(rs.getObject("activity_id", UUID.class), key -> new ArrayList<>())
                            .add(rs.getString("name"));
                });

        return spread;
    }

    @Override
    public Map<UUID, List<String>> clientSpread() {
        Map<UUID, List<String>> spread = new LinkedHashMap<>();

        jdbc.query(
                """
                select distinct u.activity_id, c.name
                from activity_usage u
                join counterparty c on c.id = u.counterparty_id
                order by u.activity_id, c.name
                """,
                rs -> {
                    spread.computeIfAbsent(rs.getObject("activity_id", UUID.class), key -> new ArrayList<>())
                            .add(rs.getString("name"));
                });

        return spread;
    }

    @Override
    public List<PriorWork> workOfThisKindIn(UUID jobId, String workType) {
        return jdbc.query(
                """
                select distinct coalesce(p.display_name, p.email) as performer_name, a.name as activity_name
                from work_node n
                join work_bracket b on b.id = n.bracket_id
                left join auth_user p on p.id = n.performer_id
                left join activity a on a.id = n.activity_id
                where b.job_id = ?
                  and b.is_boundary = false
                  and upper(b.work_type) = upper(?)
                order by performer_name, activity_name
                """,
                (rs, row) -> new PriorWork(rs.getString("performer_name"), rs.getString("activity_name")),
                jobId,
                workType);
    }

    @Override
    public void repointUsage(UUID from, UUID into) {
        jdbc.update("update work_node set activity_id = ? where activity_id = ?", into, from);
        jdbc.update("update activity_usage set activity_id = ? where activity_id = ?", into, from);
    }

    private static Activity read(ResultSet rs, int row) throws SQLException {
        Timestamp lastUsedAt = rs.getTimestamp("last_used_at");
        return Activity.rehydrated(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("slug"),
                ActivityStatus.valueOf(rs.getString("status")),
                rs.getObject("merged_into_id", UUID.class),
                rs.getInt("times_used"),
                lastUsedAt == null ? null : lastUsedAt.toInstant(),
                rs.getObject("created_by", UUID.class),
                rs.getTimestamp("created_at").toInstant());
    }
}
