package com.flowops.aiexport.infrastructure.persistence;

import com.flowops.aiexport.application.AnalyticalRecords.ConversionRecord;
import com.flowops.aiexport.application.AnalyticalRecords.InstanceRecord;
import com.flowops.aiexport.application.AnalyticalRecords.PhaseDurations;
import com.flowops.aiexport.application.AnalyticalRecords.ReviewOutcome;
import com.flowops.aiexport.application.AnalyticalRecords.StepRecord;
import com.flowops.aiexport.application.AnalyticalRecords.TaskRecord;
import com.flowops.aiexport.application.AnalyticalRecords.Transition;
import com.flowops.aiexport.application.PerExportPseudonymiser;
import com.flowops.aiexport.application.port.AnalyticalReadPort;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

@Component
public class AnalyticalReadAdapter implements AnalyticalReadPort {
    private final JdbcTemplate jdbc;

    public AnalyticalReadAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void eachTask(Window window, PerExportPseudonymiser as, Consumer<TaskRecord> sink) {
        jdbc.query(TASK_SQL, bounds(window), (RowCallbackHandler) row -> {
            UUID taskId = row.getObject("id", UUID.class);
            sink.accept(new TaskRecord(
                    taskId,
                    row.getObject("template_id", UUID.class),
                    stampedFieldsOf(row.getBigDecimal("stamped_estimated_hours")),
                    row.getObject("process_instance_id", UUID.class),
                    row.getObject("instance_step_id", UUID.class),
                    null,
                    null,
                    row.getString("priority"),
                    instant(row.getTimestamp("created_at")),
                    instant(row.getTimestamp("deadline")),
                    instant(row.getTimestamp("closed_at")),
                    row.getString("state"),
                    new PhaseDurations(
                            row.getLong("work_ms"),
                            row.getLong("blocked_ms"),
                            row.getLong("waiting_ms"),
                            row.getLong("review_ms")),
                    transitionsOf(taskId),
                    blockReasonsOf(taskId),
                    new ReviewOutcome(
                            row.getObject("first_try_approved") == null ? null : row.getBoolean("first_try_approved"),
                            row.getInt("iterations")),
                    tokenOrNull(as, row.getObject("assignee_user_id", UUID.class)),
                    tokenOrNull(as, row.getObject("creator_user_id", UUID.class))));
        });
    }

    @Override
    public void eachInstance(Window window, PerExportPseudonymiser as, Consumer<InstanceRecord> sink) {
        jdbc.query(INSTANCE_SQL, bounds(window), (RowCallbackHandler) row -> sink.accept(new InstanceRecord(
                row.getObject("id", UUID.class),
                row.getObject("template_id", UUID.class),
                row.getString("name"),
                row.getString("state"),
                instant(row.getTimestamp("started_at")),
                instant(row.getTimestamp("completed_at")),
                tokenOrNull(as, row.getObject("process_owner_user_id", UUID.class)),
                row.getInt("step_count"))));
    }

    @Override
    public void eachStep(Window window, Consumer<StepRecord> sink) {
        jdbc.query(STEP_SQL, bounds(window), (RowCallbackHandler) row -> {
            UUID stepId = row.getObject("id", UUID.class);
            Integer hours = (Integer) row.getObject("expected_duration_hours");
            sink.accept(new StepRecord(
                    stepId,
                    row.getObject("instance_id", UUID.class),
                    "DEFINITION".equals(row.getString("origin")) ? "planned" : "attached",
                    row.getString("title"),
                    row.getInt("position"),
                    row.getString("condition"),
                    dependenciesOf(stepId),
                    instant(row.getTimestamp("reachable_at")),
                    instant(row.getTimestamp("closed_at")),
                    row.getObject("task_id", UUID.class),
                    hours == null ? null : hours * 3_600_000L));
        });
    }

    @Override
    public void eachConversion(Window window, PerExportPseudonymiser as, Consumer<ConversionRecord> sink) {
        jdbc.query(CONVERSION_SQL, bounds(window), (RowCallbackHandler) row -> sink.accept(new ConversionRecord(
                as.of(row.getObject("message_id", UUID.class)),
                row.getString("conversation_kind"),
                row.getObject("converted_task_id", UUID.class),
                instant(row.getTimestamp("converted_at")))));
    }

    @Override
    public Sufficiency sufficiency(Window window) {
        return jdbc.queryForObject(
                SUFFICIENCY_SQL,
                bounds(window),
                (row, index) -> new Sufficiency(
                        row.getLong("tasks_total"),
                        row.getLong("tasks_closed"),
                        null,
                        null,
                        row.getLong("instances_completed"),
                        row.getLong("templates_with_no_completed_instance"),
                        row.getDouble("steps_attached_ratio"),
                        row.getObject("median_lifespan_days") == null ? null : row.getDouble("median_lifespan_days"),
                        row.getLong("date_range_days")));
    }

    private List<Transition> transitionsOf(UUID task) {
        return jdbc.query(
                "select from_state, to_state, occurred_at, reason from task_state_transition"
                        + " where task_id = ? order by occurred_at",
                (row, index) -> new Transition(
                        row.getString("from_state"),
                        row.getString("to_state"),
                        instant(row.getTimestamp("occurred_at")),
                        row.getString("reason")),
                task);
    }

    private List<String> blockReasonsOf(UUID task) {
        return jdbc.queryForList(
                "select reason from task_state_transition where task_id = ? and to_state = 'BLOCKED'"
                        + " and reason is not null order by occurred_at",
                String.class,
                task);
    }

    private List<UUID> dependenciesOf(UUID step) {
        return jdbc.queryForList(
                "select depends_on_step_id from instance_step_dependency where dependent_step_id = ?"
                        + " order by depends_on_step_id",
                UUID.class,
                step);
    }

    private static String tokenOrNull(PerExportPseudonymiser as, UUID person) {
        return person == null ? null : as.of(person);
    }

    private static String stampedFieldsOf(java.math.BigDecimal estimatedHours) {
        if (estimatedHours == null) {
            return null;
        }
        return "{\"estimated_hours\":" + estimatedHours.toPlainString() + "}";
    }

    private static Instant instant(Timestamp stamp) {
        return stamp == null ? null : stamp.toInstant();
    }

    private static Object[] bounds(Window window) {
        Timestamp start = window.from() == null ? null : Timestamp.from(window.from());
        Timestamp end = window.to() == null ? null : Timestamp.from(window.to());
        UUID subject = window.subjectId();
        return new Object[] {start, start, end, end, subject, subject};
    }

    @Override
    public boolean processTemplateExists(UUID subjectId) {
        Integer found =
                jdbc.queryForObject("select count(*) from process_template where id = ?", Integer.class, subjectId);
        return found != null && found > 0;
    }

    private static final String PHASE_SUMS =
            """
            coalesce(sum(case when p.phase_kind = 'ACTIVE'
                              then extract(epoch from (p.ended_at - p.started_at)) * 1000 end), 0)::bigint as work_ms,
            coalesce(sum(case when p.phase_kind = 'BLOCKED'
                              then extract(epoch from (p.ended_at - p.started_at)) * 1000 end), 0)::bigint as blocked_ms,
            coalesce(sum(case when p.phase_kind = 'WAIT'
                              then extract(epoch from (p.ended_at - p.started_at)) * 1000 end), 0)::bigint as waiting_ms,

            coalesce(sum(case when p.phase_kind in ('REVIEW', 'APPROVAL')
                              then extract(epoch from (p.ended_at - p.started_at)) * 1000 end), 0)::bigint as review_ms
            """;

    private static final String TASK_SQL =
            """
            select t.id, t.process_instance_id, t.instance_step_id, t.priority, t.created_at,
                   t.stamped_estimated_hours, t.template_id,
                   t.deadline, t.state, t.assignee_user_id, t.creator_user_id,
                   (select max(x.occurred_at) from task_state_transition x
                     where x.task_id = t.id and x.to_state = 'CLOSED') as closed_at,
                   (select count(*) from task_state_transition x
                     where x.task_id = t.id and x.to_state = 'COMPLETED') as iterations,
                   case when t.state in ('APPROVED', 'CLOSED')
                        then (select count(*) = 1 from task_state_transition x
                               where x.task_id = t.id and x.to_state = 'COMPLETED')
                   end as first_try_approved,
            """
                    + PHASE_SUMS
                    + """
            from task t
            left join task_phase_timer p on p.task_id = t.id and p.ended_at is not null
            where (?::timestamptz is null or t.created_at >= ?
                   or exists (select 1 from task_state_transition c
                               where c.task_id = t.id and c.to_state = 'CLOSED'))
              and (?::timestamptz is null or t.created_at <= ?)

              and (?::uuid is null
                   or t.process_instance_id in (select id from process_instance where template_id = ?))
            group by t.id
            order by t.created_at
            """;

    private static final String INSTANCE_SQL =
            """
            select i.id, i.template_id, i.name, i.state, i.started_at,
                   (select max(s.closed_at) from instance_step s where s.instance_id = i.id) as completed_at,
                   i.process_owner_user_id,
                   (select count(*) from instance_step s where s.instance_id = i.id) as step_count
            from process_instance i
            where (?::timestamptz is null or i.started_at >= ?)
              and (?::timestamptz is null or i.started_at <= ?)
              and (?::uuid is null or i.template_id = ?)
            order by i.started_at
            """;

    private static final String STEP_SQL =
            """
            select s.id, s.instance_id, s.origin, s.title, s.position, s.condition,
                   s.reachable_at, s.closed_at, s.task_id, s.expected_duration_hours
            from instance_step s
            join process_instance i on i.id = s.instance_id
            where (?::timestamptz is null or i.started_at >= ?)
              and (?::timestamptz is null or i.started_at <= ?)

              and (?::uuid is null or i.template_id = ?)
            order by s.instance_id, s.position
            """;

    private static final String CONVERSION_SQL =
            """
            select m.id as message_id, c.kind as conversation_kind, m.converted_task_id,
                   t.created_at as converted_at
            from message m
            join conversation c on c.id = m.conversation_id
            join task t on t.id = m.converted_task_id
            where m.converted_task_id is not null
              and (?::timestamptz is null or t.created_at >= ?)
              and (?::timestamptz is null or t.created_at <= ?)
              and (?::uuid is null
                   or t.process_instance_id in (select id from process_instance where template_id = ?))
            order by t.created_at
            """;

    private static final String SUFFICIENCY_SQL =
            """
            select
              (select count(*) from task) as tasks_total,
              (select count(*) from task where state = 'CLOSED') as tasks_closed,
              (select count(*) from process_instance where state = 'COMPLETE') as instances_completed,
              (select count(*) from process_template pt
                where not exists (select 1 from process_instance i
                                   where i.template_id = pt.id and i.state = 'COMPLETE'))
                as templates_with_no_completed_instance,
              coalesce((select count(*) filter (where origin = 'ATTACHED')::float
                          / nullif(count(*), 0) from instance_step), 0) as steps_attached_ratio,
              (select percentile_cont(0.5) within group (
                        order by extract(epoch from (x.closed_at - t.created_at)) / 86400.0)
                 from task t
                 join lateral (select max(occurred_at) as closed_at from task_state_transition
                                where task_id = t.id and to_state = 'CLOSED') x on true
                where x.closed_at is not null) as median_lifespan_days,
              coalesce((select (max(created_at)::date - min(created_at)::date) from task), 0) as date_range_days
            where (?::timestamptz is null or ?::timestamptz is not null)
              and (?::timestamptz is null or ?::timestamptz is not null)
              and (?::uuid is null or ?::uuid is not null)
            """;
}
