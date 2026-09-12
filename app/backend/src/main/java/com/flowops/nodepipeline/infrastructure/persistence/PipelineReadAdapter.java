package com.flowops.nodepipeline.infrastructure.persistence;

import com.flowops.nodepipeline.application.port.PipelineReadPort;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PipelineReadAdapter implements PipelineReadPort {
    private final JdbcTemplate jdbc;

    public PipelineReadAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<RunRecord> latestRun() {
        return jdbc
                .query(
                        """
                        select id, window_from, window_to, started_at, finished_at, reached_stage,
                               signature, ai_mode, nodes_read, nodes_in_window, jobs_read, failure
                          from analysis_run
                         where signature is not null
                         order by started_at desc
                         limit 1
                        """,
                        (ResultSet row, int index) -> new RunRecord(
                                row.getObject("id", UUID.class),
                                instant(row.getTimestamp("window_from")),
                                instant(row.getTimestamp("window_to")),
                                instant(row.getTimestamp("started_at")),
                                instant(row.getTimestamp("finished_at")),
                                row.getString("reached_stage"),
                                row.getString("signature"),
                                row.getString("ai_mode"),
                                row.getInt("nodes_read"),
                                row.getInt("nodes_in_window"),
                                row.getInt("jobs_read"),
                                row.getString("failure")))
                .stream()
                .findFirst();
    }

    @Override
    public List<StageTally> stagesOf(UUID runId) {
        return jdbc.query(
                """
                select finding_kind as stage, deterministic_outcome as outcome, count(*) as n
                  from pipeline_decision
                 where run_id = ?
                 group by finding_kind, deterministic_outcome
                 order by finding_kind, count(*) desc
                """,
                (ResultSet row, int index) ->
                        new StageTally(row.getString("stage"), row.getString("outcome"), row.getInt("n")),
                runId);
    }

    @Override
    public List<RunRecord> history(int limit) {
        return jdbc.query(
                """
                select id, window_from, window_to, started_at, finished_at, reached_stage,
                       signature, ai_mode, nodes_read, nodes_in_window, jobs_read, failure
                  from analysis_run
                 where signature is not null
                 order by started_at desc
                 limit ?
                """,
                (ResultSet row, int index) -> new RunRecord(
                        row.getObject("id", UUID.class),
                        instant(row.getTimestamp("window_from")),
                        instant(row.getTimestamp("window_to")),
                        instant(row.getTimestamp("started_at")),
                        instant(row.getTimestamp("finished_at")),
                        row.getString("reached_stage"),
                        row.getString("signature"),
                        row.getString("ai_mode"),
                        row.getInt("nodes_read"),
                        row.getInt("nodes_in_window"),
                        row.getInt("jobs_read"),
                        row.getString("failure")),
                limit);
    }

    @Override
    public List<ItemMovement> movementsBetween(UUID before, UUID after) {
        return jdbc.query(
                """
                select coalesce(b.item_id, a.item_id)     as item_id,
                       coalesce(b.item_kind, a.item_kind) as item_kind,
                       b.last_stage as before_stage, b.reason as before_reason,
                       a.last_stage as after_stage,  a.reason as after_reason
                  from (select * from pipeline_item_stage where run_id = ?) b
                  full outer join (select * from pipeline_item_stage where run_id = ?) a
                    on a.item_id = b.item_id and a.item_kind = b.item_kind
                 where b.item_id is null
                    or a.item_id is null
                    or b.last_stage is distinct from a.last_stage
                    or b.reason is distinct from a.reason
                 order by item_kind, item_id
                """,
                (ResultSet row, int index) -> new ItemMovement(
                        row.getString("item_id"),
                        row.getString("item_kind"),
                        row.getString("before_stage"),
                        row.getString("before_reason"),
                        row.getString("after_stage"),
                        row.getString("after_reason")),
                before,
                after);
    }

    @Override
    public List<StuckRow> stuckIn(UUID runId) {
        return jdbc.query(
                """
                select min(item_id) as example, item_kind, last_stage, reason, count(*) as n
                  from pipeline_item_stage
                 where run_id = ?
                 group by item_kind, last_stage, reason
                 order by count(*) desc, reason
                """,
                (ResultSet row, int index) -> new StuckRow(
                        row.getString("example"),
                        row.getString("item_kind"),
                        row.getString("last_stage"),
                        row.getString("reason"),
                        row.getInt("n")),
                runId);
    }

    private static final String SOURCES =
            """
            select s.subject_kind as kind, s.subject_id as id,
                   coalesce(nullif(n.title, ''), nullif(n.text, ''), s.subject_id::text) as label,
                   concat_ws(' · ', n.work_type, j.name) as detail,
                   n.conversation_id, n.job_id
              from pipeline_decision_subject s
              join work_node n  on n.id = s.subject_id
              left join job j   on j.id = n.job_id
             where s.decision_id = ? and s.subject_kind = 'NODE'

            union all

            select s.subject_kind, s.subject_id,
                   coalesce(nullif(j.name, ''), s.subject_id::text),
                   j.status, null, j.id
              from pipeline_decision_subject s
              join job j on j.id = s.subject_id
             where s.decision_id = ? and s.subject_kind = 'JOB'

            union all

            select s.subject_kind, s.subject_id,
                   coalesce(nullif(t.title, ''), s.subject_id::text),
                   concat_ws(' · ', t.status, t.work_type), null, null
              from pipeline_decision_subject s
              join task_template t on t.id = s.subject_id
             where s.decision_id = ? and s.subject_kind = 'TEMPLATE'

             order by 1, 3
            """;

    @Override
    public List<DecisionSource> sourcesOf(UUID decisionId) {
        return jdbc.query(
                SOURCES,
                (ResultSet row, int index) -> new DecisionSource(
                        row.getString("kind"),
                        row.getObject("id", UUID.class),
                        row.getString("label"),
                        row.getString("detail"),
                        row.getObject("conversation_id", UUID.class),
                        row.getObject("job_id", UUID.class)),
                decisionId,
                decisionId,
                decisionId);
    }

    @Override
    public List<FindingRow> findingsIn(UUID runId, String findingKind, int limit) {
        return jdbc.query(
                """
                select id, finding_kind, subject, deterministic_outcome, score, reason, outcome
                  from pipeline_decision
                 where run_id = ?
                   and finding_kind = ?
                   and deterministic_outcome <> 'ABSTAIN'
                 order by score desc nulls last, subject
                 limit ?
                """,
                (ResultSet row, int index) -> new FindingRow(
                        row.getObject("id", UUID.class),
                        row.getString("finding_kind"),
                        row.getString("subject"),
                        row.getString("deterministic_outcome"),
                        row.getObject("score", java.math.BigDecimal.class) == null
                                ? null
                                : row.getBigDecimal("score").doubleValue(),
                        row.getString("reason"),
                        row.getString("outcome")),
                runId,
                findingKind,
                limit);
    }

    private static final String COMPARISON =
            """
            select r.ai_mode,
                   count(*)                                                              as compared,
                   count(*) filter (where d.ai_outcome is null and not d.ai_failed)      as agreed,
                   count(*) filter (where d.ai_outcome is not null
                                      and d.deterministic_outcome = 'ABSTAIN'
                                      and d.ai_outcome <> 'ABSTAIN')                     as raised,
                   count(*) filter (where d.ai_outcome = 'ABSTAIN'
                                      and d.deterministic_outcome <> 'ABSTAIN')          as lowered,
                   count(*) filter (where d.ai_outcome is not null
                                      and d.ai_outcome <> 'ABSTAIN'
                                      and d.deterministic_outcome <> 'ABSTAIN'
                                      and d.ai_outcome <> d.deterministic_outcome)       as changed,
                   count(*) filter (where d.ai_failed)                                   as failed,
                   max(d.model_id)                                                       as model_id,
                   max(d.prompt_version)                                                 as prompt_version
              from analysis_run r
              join pipeline_decision d on d.run_id = r.id
             where r.id = ? and r.ai_mode = 'COMPARE' and d.finding_kind = 'NODE_MATCH'
             group by r.ai_mode
            """;

    @Override
    public java.util.Optional<Comparison> comparisonOf(java.util.UUID runId) {
        return jdbc
                .query(
                        COMPARISON,
                        (row, index) -> new Comparison(
                                runId,
                                row.getString("ai_mode"),
                                row.getInt("compared"),
                                row.getInt("agreed"),
                                row.getInt("raised"),
                                row.getInt("lowered"),
                                row.getInt("changed"),
                                row.getInt("failed"),
                                row.getString("model_id"),
                                row.getString("prompt_version")),
                        runId)
                .stream()
                .findFirst();
    }

    private static java.time.Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
