package com.flowops.nodepipeline.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowops.nodepipeline.application.port.PipelineJournalPort;
import com.flowops.nodepipeline.domain.NodeVerdict;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PipelineJournalAdapter implements PipelineJournalPort {
    private static final String STORE_SUBJECT =
            """
            insert into pipeline_decision_subject (decision_id, subject_kind, subject_id)
            values (?, ?, ?::uuid)
            on conflict do nothing
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public PipelineJournalAdapter(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public UUID openRun(Instant windowFrom, Instant windowTo, String signature, AiMode aiMode, Instant startedAt) {
        UUID runId = UUID.randomUUID();
        jdbc.update(
                """
                insert into analysis_run (id, window_from, window_to, started_at, reached_stage,
                                          signature, ai_mode)
                values (?, ?, ?, ?, 'OBSERVE', ?, ?)
                """,
                runId,
                Timestamp.from(windowFrom),
                Timestamp.from(windowTo),
                Timestamp.from(startedAt),
                signature,
                aiMode.name());
        return runId;
    }

    @Override
    public void recordVolumes(UUID runId, int nodesRead, int nodesInWindow, int jobsRead) {
        jdbc.update(
                "update analysis_run set nodes_read = ?, nodes_in_window = ?, jobs_read = ? where id = ?",
                nodesRead,
                nodesInWindow,
                jobsRead,
                runId);
    }

    @Override
    public void recordDecisions(
            UUID runId, List<NodeVerdict> verdicts, Instant at, String modelId, String promptVersion) {
        if (verdicts.isEmpty()) {
            return;
        }
        Map<UUID, NodeVerdict> byDecision = identify(verdicts);

        jdbc.batchUpdate(
                """
                insert into pipeline_decision (id, run_id, finding_kind, subject, subject_template,
                                               deterministic_outcome, score, confidence, separation,
                                               reason, coverage, text_score, finalists, created_at,
                                               ai_outcome, ai_confidence, model_id, prompt_version)
                values (?, ?, 'NODE_MATCH', ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, ?)
                """,
                byDecision.entrySet().stream()
                        .map(entry -> {
                            NodeVerdict v = entry.getValue();
                            return new Object[] {
                                entry.getKey(),
                                runId,
                                v.nodeId(),
                                v.topTemplateId(),
                                v.tier().name(),
                                v.score(),
                                v.confidence(),
                                v.separation(),
                                v.why(),
                                v.coverage(),
                                v.textScore(),
                                finalistsAsJson(v),
                                Timestamp.from(at),
                                v.aiOutcome(),
                                v.aiConfidence(),
                                modelId,
                                promptVersion
                            };
                        })
                        .toList());

        List<Object[]> subjects = new ArrayList<>();
        byDecision.forEach((decisionId, verdict) -> {
            if (isUuid(verdict.topTemplateId())) {
                subjects.add(new Object[] {decisionId, "TEMPLATE", verdict.topTemplateId()});
            }
        });
        writeSubjects(subjects);
    }

    @Override
    public void recordComparedDecisions(UUID runId, List<Comparison> comparisons, Instant at) {
        if (comparisons.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(
                """
                insert into pipeline_decision (id, run_id, finding_kind, subject, subject_template,
                                               deterministic_outcome, score, confidence, separation,
                                               reason, ai_outcome, ai_reason, ai_confidence, ai_failed,
                                               model_id, prompt_version, created_at)
                values (?, ?, 'NODE_MATCH', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                comparisons.stream()
                        .map(c -> {
                            NodeVerdict plain = c.deterministic();
                            NodeVerdict judged = c.withAi();
                            return new Object[] {
                                UUID.randomUUID(),
                                runId,
                                plain.nodeId(),
                                plain.topTemplateId(),
                                plain.tier().name(),
                                plain.score(),
                                plain.confidence(),
                                plain.separation(),
                                plain.why(),
                                judged == null ? null : judged.tier().name(),
                                judged == null ? null : judged.why(),
                                judged == null ? null : judged.confidence(),
                                c.aiFailed(),
                                c.modelId(),
                                c.promptVersion(),
                                Timestamp.from(at)
                            };
                        })
                        .toList());
    }

    @Override
    public void recordJobDecisions(
            UUID runId, List<com.flowops.nodepipeline.domain.job.JobVerdict> verdicts, Instant at) {
        if (verdicts.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(
                """
                insert into pipeline_decision (id, run_id, finding_kind, subject, subject_template,
                                               deterministic_outcome, score, separation, reason, created_at)
                values (?, ?, 'JOB_MATCH', ?, ?, ?, ?, ?, ?, ?)
                """,
                verdicts.stream()
                        .map(v -> new Object[] {
                            UUID.randomUUID(),
                            runId,
                            v.jobId(),
                            v.processId(),
                            v.tier() == com.flowops.nodepipeline.domain.job.JobTier.PROCESS_RUN ? "OK" : "ABSTAIN",
                            v.score(),
                            v.separation(),
                            v.tier().name().toLowerCase(java.util.Locale.ROOT) + ": " + v.why(),
                            Timestamp.from(at)
                        })
                        .toList());
    }

    @Override
    public void recordDiscoveries(
            UUID runId,
            List<com.flowops.nodepipeline.domain.discovery.StepKind> kinds,
            List<com.flowops.nodepipeline.domain.discovery.DiscoveredProcess> processes,
            Instant at) {
        List<Object[]> subjects = new ArrayList<>();

        if (!kinds.isEmpty()) {
            Map<UUID, com.flowops.nodepipeline.domain.discovery.StepKind> byDecision = identify(kinds);
            jdbc.batchUpdate(
                    """
                    insert into pipeline_decision (id, run_id, finding_kind, subject, deterministic_outcome,
                                                   score, reason, created_at)
                    values (?, ?, 'STEP_KIND', ?, 'NUDGE', ?, ?, ?)
                    """,
                    byDecision.entrySet().stream()
                            .map(entry -> {
                                var k = entry.getValue();
                                return new Object[] {
                                    entry.getKey(),
                                    runId,
                                    k.id(),
                                    k.certainty(),
                                    "%d marks across %d jobs, cohesion %.3f"
                                            .formatted(
                                                    k.nodeIds().size(),
                                                    k.jobIds().size(),
                                                    k.cohesion()),
                                    Timestamp.from(at)
                                };
                            })
                            .toList());

            byDecision.forEach((decisionId, kind) -> {
                kind.nodeIds().forEach(nodeId -> subjects.add(new Object[] {decisionId, "NODE", nodeId}));
                kind.jobIds().forEach(jobId -> subjects.add(new Object[] {decisionId, "JOB", jobId}));
            });
        }

        if (!processes.isEmpty()) {
            Map<UUID, com.flowops.nodepipeline.domain.discovery.DiscoveredProcess> byDecision = identify(processes);
            jdbc.batchUpdate(
                    """
                    insert into pipeline_decision (id, run_id, finding_kind, subject, deterministic_outcome,
                                                   score, reason, created_at)
                    values (?, ?, 'DRAFT_PROCESS', ?, 'NUDGE', ?, ?, ?)
                    """,
                    byDecision.entrySet().stream()
                            .map(entry -> {
                                var p = entry.getValue();
                                return new Object[] {
                                    entry.getKey(),
                                    runId,
                                    String.join(" -> ", p.displayOrder()),
                                    p.certainty(),
                                    "%d runs, %s (%.3f)"
                                            .formatted(
                                                    p.runs(),
                                                    p.orderReliable() ? "order reliable" : p.orderWithheldReason(),
                                                    p.orderConfidence()),
                                    Timestamp.from(at)
                                };
                            })
                            .toList());

            byDecision.forEach((decisionId, process) ->
                    process.jobIds().forEach(jobId -> subjects.add(new Object[] {decisionId, "JOB", jobId})));
        }

        writeSubjects(subjects);
    }

    private static <T> Map<UUID, T> identify(List<T> items) {
        Map<UUID, T> byDecision = new LinkedHashMap<>();
        items.forEach(item -> byDecision.put(UUID.randomUUID(), item));
        return byDecision;
    }

    private void writeSubjects(List<Object[]> subjects) {
        if (!subjects.isEmpty()) {
            jdbc.batchUpdate(STORE_SUBJECT, subjects);
        }
    }

    private String finalistsAsJson(NodeVerdict verdict) {
        if (verdict.finalists().isEmpty()) {
            return null;
        }
        try {
            return json.writeValueAsString(verdict.finalists());
        } catch (JsonProcessingException impossible) {
            return null;
        }
    }

    private static boolean isUuid(String value) {
        if (value == null) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException notAUuid) {
            return false;
        }
    }

    @Override
    public void recordStuck(UUID runId, List<StuckItem> stuck) {
        if (stuck.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(
                """
                insert into pipeline_item_stage (run_id, item_id, item_kind, last_stage, reason)
                values (?, ?, ?, ?, ?)
                on conflict (run_id, item_id, item_kind) do update set
                    last_stage = excluded.last_stage,
                    reason     = excluded.reason
                """,
                stuck.stream()
                        .map(s -> new Object[] {runId, s.itemId(), s.itemKind(), s.lastStage(), s.reason()})
                        .toList());
    }

    @Override
    public void closeRun(UUID runId, String reachedStage, String failure, Instant finishedAt) {
        jdbc.update(
                "update analysis_run set reached_stage = ?, failure = ?, finished_at = ? where id = ?",
                reachedStage,
                failure,
                Timestamp.from(finishedAt),
                runId);
    }
}
