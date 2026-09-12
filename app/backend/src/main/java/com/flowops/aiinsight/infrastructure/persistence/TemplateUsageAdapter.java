package com.flowops.aiinsight.infrastructure.persistence;

import com.flowops.aiinsight.application.port.TemplateUsagePort;
import com.flowops.aiinsight.domain.EstimateDivergenceDetection.ClosedTask;
import com.flowops.aiinsight.domain.UnusedTemplateDetection.TemplateUsage;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TemplateUsageAdapter implements TemplateUsagePort {
    private final JdbcTemplate jdbc;

    public TemplateUsageAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Usage> of(UUID templateId) {
        return jdbc
                .query(
                        """
                        select t.id,
                               t.title,
                               t.status,
                               t.approved_at,
                               t.times_used,
                               t.checklist::text as checklist,
                               t.estimated_hours,
                               (select max(k.created_at) from task k where k.template_id = t.id) as last_used_at,
                               (select count(*) from task k
                                 where k.template_id = t.id
                                   and not exists (select 1 from task_state_transition x
                                                    where x.task_id = k.id and x.to_state = 'CLOSED')
                               ) as never_closed
                        from task_template t
                        where t.id = ?
                        """,
                        (row, index) -> new Usage(
                                new TemplateUsage(
                                        row.getObject("id", UUID.class),
                                        row.getString("title"),
                                        "APPROVED".equals(row.getString("status"))
                                                ? instant(row.getTimestamp("approved_at"))
                                                : null,
                                        instant(row.getTimestamp("last_used_at")),
                                        row.getInt("times_used"),
                                        stampsOf(templateId)),
                                closedWorkOf(templateId),
                                row.getInt("never_closed"),
                                estimateMs(row.getBigDecimal("estimated_hours")),
                                null,
                                List.of(
                                        String.valueOf(row.getString("title")),
                                        String.valueOf(row.getString("checklist")))),
                        templateId)
                .stream()
                .findFirst();
    }

    private List<ClosedTask> closedWorkOf(UUID templateId) {
        return jdbc.query(
                """
                select k.id,
                       k.stamped_estimated_hours,
                       coalesce(sum(case when p.phase_kind = 'ACTIVE'
                                         then extract(epoch from (p.ended_at - p.started_at)) * 1000 end), 0)::bigint
                           as work_ms
                from task k
                left join task_phase_timer p on p.task_id = k.id and p.ended_at is not null
                where k.template_id = ?
                  and exists (select 1 from task_state_transition x
                               where x.task_id = k.id and x.to_state = 'CLOSED')
                group by k.id, k.stamped_estimated_hours
                """,
                (row, index) -> new ClosedTask(
                        row.getLong("work_ms"), estimateMs(row.getBigDecimal("stamped_estimated_hours"))),
                templateId);
    }

    private static Long estimateMs(java.math.BigDecimal hours) {
        return hours == null
                ? null
                : hours.multiply(java.math.BigDecimal.valueOf(3_600_000L)).longValue();
    }

    private List<Instant> stampsOf(UUID templateId) {
        return jdbc.query(
                "select created_at from task where template_id = ? order by created_at",
                (row, index) -> instant(row.getTimestamp("created_at")),
                templateId);
    }

    private static Instant instant(Timestamp stamp) {
        return stamp == null ? null : stamp.toInstant();
    }
}
