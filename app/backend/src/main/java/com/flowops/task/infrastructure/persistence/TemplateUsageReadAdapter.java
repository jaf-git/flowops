package com.flowops.task.infrastructure.persistence;

import com.flowops.task.application.published.TemplateUsageReadPort;
import com.flowops.task.application.published.TemplateUsageUseCase;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TemplateUsageReadAdapter implements TemplateUsageReadPort {
    private final JdbcTemplate jdbc;

    public TemplateUsageReadAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public TemplateUsageUseCase.Usage usageOf(UUID templateId, Instant now) {
        return new TemplateUsageUseCase.Usage(
                count("select count(*) from task where template_id = ?", templateId),
                live(templateId, now),
                activeTime(templateId),
                firstTryApproval(templateId));
    }

    @Override
    public List<UUID> stampedTaskIds(UUID templateId) {
        return jdbc.queryForList("select id from task where template_id = ?", UUID.class, templateId);
    }

    @Override
    public List<TemplateUsageUseCase.TaskRow> rowsOf(UUID templateId, TemplateUsageUseCase.LiveBand band, Instant now) {
        return jdbc.query(
                """
                select t.id, t.title, t.state, t.deadline, t.assignee_user_id, u.display_name
                from task t
                left join auth_user u on u.id = t.assignee_user_id
                where t.template_id = ?
                  and
                """
                        + BAND_PREDICATE.get(band)
                        + """

                order by t.deadline asc nulls last, t.title asc
                """,
                (row, index) -> new TemplateUsageUseCase.TaskRow(
                        row.getObject("id", UUID.class),
                        row.getString("title"),
                        row.getObject("assignee_user_id", UUID.class),
                        row.getString("display_name"),
                        row.getString("state"),
                        instant(row.getTimestamp("deadline"))),
                band == TemplateUsageUseCase.LiveBand.OVERDUE
                        ? new Object[] {templateId, Timestamp.from(now)}
                        : new Object[] {templateId});
    }

    private static final java.util.Map<TemplateUsageUseCase.LiveBand, String> BAND_PREDICATE = java.util.Map.of(
            TemplateUsageUseCase.LiveBand.NOT_STARTED, "t.state in ('CREATED', 'ACCEPTED')",
            TemplateUsageUseCase.LiveBand.RUNNING, "t.state = 'IN_PROGRESS'",
            TemplateUsageUseCase.LiveBand.BLOCKED, "t.state = 'BLOCKED'",
            TemplateUsageUseCase.LiveBand.IN_REVIEW, "t.state in ('COMPLETED', 'APPROVED')",
            TemplateUsageUseCase.LiveBand.FINISHED, "t.state = 'CLOSED'",
            TemplateUsageUseCase.LiveBand.OVERDUE, "t.state <> 'CLOSED' and t.deadline is not null and t.deadline < ?");

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private TemplateUsageUseCase.LiveCounts live(UUID templateId, Instant now) {
        return jdbc.queryForObject(
                """
                select
                  count(*) filter (where %s) as not_started,
                  count(*) filter (where %s) as running,
                  count(*) filter (where %s) as blocked,
                  count(*) filter (where %s) as in_review,
                  count(*) filter (where %s) as finished,
                  count(*) filter (where %s) as overdue
                from task t where t.template_id = ?
                """
                        .formatted(
                                BAND_PREDICATE.get(TemplateUsageUseCase.LiveBand.NOT_STARTED),
                                BAND_PREDICATE.get(TemplateUsageUseCase.LiveBand.RUNNING),
                                BAND_PREDICATE.get(TemplateUsageUseCase.LiveBand.BLOCKED),
                                BAND_PREDICATE.get(TemplateUsageUseCase.LiveBand.IN_REVIEW),
                                BAND_PREDICATE.get(TemplateUsageUseCase.LiveBand.FINISHED),
                                BAND_PREDICATE.get(TemplateUsageUseCase.LiveBand.OVERDUE)),
                (row, index) -> new TemplateUsageUseCase.LiveCounts(
                        row.getInt("not_started"),
                        row.getInt("running"),
                        row.getInt("blocked"),
                        row.getInt("in_review"),
                        row.getInt("finished"),
                        row.getInt("overdue")),
                Timestamp.from(now),
                templateId);
    }

    private TemplateUsageUseCase.ActiveTime activeTime(UUID templateId) {
        return jdbc.queryForObject(
                """
                with per_task as (
                    select t.id,
                           sum(extract(epoch from (p.ended_at - p.started_at))) as active_seconds
                    from task t
                    join task_phase_timer p on p.task_id = t.id
                    where t.template_id = ?
                      and p.phase_kind = 'ACTIVE'
                      and p.ended_at is not null
                    group by t.id
                )
                select
                  percentile_cont(0.5)  within group (order by active_seconds) as median,
                  percentile_cont(0.25) within group (order by active_seconds) as lower_quartile,
                  percentile_cont(0.75) within group (order by active_seconds) as upper_quartile,
                  count(*) as measured
                from per_task
                """,
                (row, index) -> new TemplateUsageUseCase.ActiveTime(
                        seconds(row.getObject("median")),
                        seconds(row.getObject("lower_quartile")),
                        seconds(row.getObject("upper_quartile")),
                        row.getInt("measured")),
                templateId);
    }

    private TemplateUsageUseCase.FirstTryApproval firstTryApproval(UUID templateId) {
        return jdbc.queryForObject(
                """
                with reviewed as (
                    select t.id,
                           count(*) filter (where e.action = 'TASK_RETURNED_FOR_REWORK') as returns
                    from task t
                    join task_approval a on a.task_id = t.id
                    left join task_event e on e.task_id = t.id
                    where t.template_id = ?
                    group by t.id
                )
                select count(*) filter (where returns = 0) as passed, count(*) as reviewed from reviewed
                """,
                (row, index) -> new TemplateUsageUseCase.FirstTryApproval(row.getInt("passed"), row.getInt("reviewed")),
                templateId);
    }

    private static Long seconds(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private int count(String sql, UUID templateId) {
        Integer total = jdbc.queryForObject(sql, Integer.class, templateId);
        return total == null ? 0 : total;
    }
}
