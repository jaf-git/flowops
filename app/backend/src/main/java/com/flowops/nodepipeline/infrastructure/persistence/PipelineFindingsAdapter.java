package com.flowops.nodepipeline.infrastructure.persistence;

import com.flowops.nodepipeline.application.port.PipelineFindingsPort;
import com.flowops.nodepipeline.domain.MatchTier;
import com.flowops.nodepipeline.domain.job.JobTier;
import com.flowops.nodepipeline.domain.notify.DiscoveryFinding;
import com.flowops.nodepipeline.domain.notify.JobFinding;
import com.flowops.nodepipeline.domain.notify.NodeFinding;
import com.flowops.nodepipeline.domain.notify.NudgeHistory;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PipelineFindingsAdapter implements PipelineFindingsPort {
    private final JdbcTemplate jdbc;

    public PipelineFindingsAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<RunFindings> latestRun() {
        List<UUID> runs = jdbc.query(
                """
                select id
                  from analysis_run
                 where signature is not null
                 order by started_at desc
                 limit 1
                """,
                (ResultSet row, int index) -> row.getObject("id", UUID.class));

        if (runs.isEmpty()) {
            return Optional.empty();
        }
        UUID runId = runs.get(0);

        List<NodeFinding> nodes = jdbc.query(
                """
                select d.id, d.subject, d.subject_template, d.deterministic_outcome, d.score, d.reason,
                       n.job_id
                  from pipeline_decision d

                  left join work_node n on n.id::text = d.subject
                 where d.run_id = ?
                   and d.finding_kind = 'NODE_MATCH'
                 order by d.subject
                """,
                (ResultSet row, int index) -> new NodeFinding(
                        row.getObject("id", UUID.class),
                        row.getString("subject"),
                        row.getString("job_id"),
                        row.getString("subject_template"),
                        tierOf(row.getString("deterministic_outcome")),
                        row.getDouble("score"),
                        reasonOrDefault(row.getString("reason"))),
                runId);

        List<JobFinding> jobs = jdbc.query(
                """
                select id, subject, subject_template, score, reason
                  from pipeline_decision
                 where run_id = ?
                   and finding_kind = 'JOB_MATCH'
                 order by subject
                """,
                (ResultSet row, int index) -> new JobFinding(
                        row.getObject("id", UUID.class),
                        row.getString("subject"),
                        jobTierOf(row.getString("reason")),
                        row.getString("subject_template"),
                        row.getDouble("score"),
                        reasonOrDefault(row.getString("reason"))),
                runId);

        List<DiscoveryFinding> discoveries = jdbc.query(
                """
                select id, finding_kind, subject, score, reason
                  from pipeline_decision
                 where run_id = ?
                   and finding_kind in ('STEP_KIND', 'DRAFT_PROCESS')
                 order by score desc nulls last, subject
                """,
                (ResultSet row, int index) -> new DiscoveryFinding(
                        row.getObject("id", UUID.class),
                        "STEP_KIND".equals(row.getString("finding_kind"))
                                ? DiscoveryFinding.Grain.STEP_KIND
                                : DiscoveryFinding.Grain.DRAFT_PROCESS,
                        row.getString("subject"),
                        row.getDouble("score"),
                        reasonOrDefault(row.getString("reason"))),
                runId);

        return Optional.of(new RunFindings(runId, nodes, jobs, discoveries));
    }

    @Override
    public Set<String> shownBefore(UUID runId) {
        return new HashSet<>(jdbc.query(
                """
                select finding_kind, subject, subject_template, reason
                  from pipeline_decision
                 where cta_shown = true
                   and run_id <> ?
                """,
                (ResultSet row, int index) -> keyOf(
                        row.getString("finding_kind"),
                        row.getString("subject"),
                        row.getString("subject_template"),
                        row.getString("reason")),
                runId));
    }

    @Override
    public List<NudgeHistory.PriorNudge> priorNudges(UUID runId) {
        return jdbc.query(
                """
                select coalesce(n.marker_id, n.creator_id) as person, d.subject_template
                  from pipeline_decision d
                  join work_node n on n.id::text = d.subject
                 where d.cta_shown = true
                   and d.run_id <> ?
                   and d.finding_kind = 'NODE_MATCH'
                   and d.subject_template is not null
                   and coalesce(n.marker_id, n.creator_id) is not null
                """,
                (ResultSet row, int index) -> new NudgeHistory.PriorNudge(
                        row.getObject("person", UUID.class), row.getString("subject_template")),
                runId);
    }

    @Override
    public void markShown(Collection<UUID> decisionIds) {
        if (decisionIds.isEmpty()) {
            return;
        }

        jdbc.update("update pipeline_decision set cta_shown = true where id = any(?)", (Object)
                decisionIds.toArray(UUID[]::new));
    }

    private static MatchTier tierOf(String outcome) {
        try {
            return MatchTier.valueOf(outcome);
        } catch (IllegalArgumentException unknown) {
            return MatchTier.ABSTAIN;
        }
    }

    private static JobTier jobTierOf(String reason) {
        if (reason == null || !reason.contains(":")) {
            return JobTier.IN_PROGRESS;
        }
        try {
            return JobTier.valueOf(
                    reason.substring(0, reason.indexOf(':')).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return JobTier.IN_PROGRESS;
        }
    }

    private static String keyOf(String findingKind, String subject, String template, String reason) {
        return switch (findingKind) {
            case "NODE_MATCH" -> "NODE_MATCH|" + subject + "|" + template;
            case "JOB_MATCH" -> "JOB_MATCH|" + subject + "|" + jobTierOf(reason);
            default -> findingKind + "|" + subject;
        };
    }

    private static String reasonOrDefault(String reason) {
        return reason == null || reason.isBlank() ? "recorded without a stated reason" : reason;
    }
}
