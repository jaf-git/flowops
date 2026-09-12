package com.flowops.discovery.infrastructure.analysis;

import com.flowops.discovery.application.analysis.AnalysisStorePort;
import com.flowops.discovery.application.analysis.Phrasing;
import com.flowops.discovery.domain.analysis.Finding;
import com.flowops.discovery.domain.analysis.Recommendation;
import com.flowops.discovery.domain.analysis.Stage;
import com.flowops.discovery.domain.model.BracketId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AnalysisStoreAdapter implements AnalysisStorePort {
    private final JdbcTemplate jdbc;

    public AnalysisStoreAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void openRun(UUID runId, Instant from, Instant to, Instant startedAt, int bracketsRead, int waitsRead) {
        jdbc.update(
                """
                insert into analysis_run (
                    id, window_from, window_to, started_at, brackets_read, waits_read, reached_stage,
                    produced_by)
                values (?,?,?,?,?,?,?,'DISCOVERY')
                """,
                runId,
                Timestamp.from(from),
                Timestamp.from(to),
                Timestamp.from(startedAt),
                bracketsRead,
                waitsRead,
                Stage.OBSERVE.name());
    }

    @Override
    public UUID record(UUID runId, Finding finding, Stage stage, Phrasing.Phrased phrased, Instant at) {
        UUID findingId = UUID.randomUUID();

        jdbc.update(
                """
                insert into analysis_finding (
                    id, run_id, detector, stage, subject_kind, subject_key,
                    headline, sample_size, measure, measure_unit, phrasing, phrased_by, created_at)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                findingId,
                runId,
                finding.detector(),
                stage.name(),
                finding.subjectKind().name(),
                finding.subjectKey(),
                finding.headline(),
                finding.sampleSize(),
                finding.measure(),
                finding.unit(),
                phrased.sentence(),
                phrased.by().name(),
                Timestamp.from(at));

        for (BracketId subject : finding.subjects()) {
            jdbc.update(
                    """
                    insert into finding_subject (finding_id, subject_kind, subject_id)
                    values (?, 'BRACKET', ?)
                    on conflict do nothing
                    """,
                    findingId,
                    subject.value());
        }

        return findingId;
    }

    @Override
    public void propose(UUID runId, UUID findingId, Recommendation recommendation, Instant at) {
        jdbc.update(
                """
                insert into analysis_recommendation (
                    id, run_id, finding_id, kind, headline, detail, confidence, created_at)
                values (?,?,?,?,?,?,?,?)
                """,
                UUID.randomUUID(),
                runId,
                findingId,
                recommendation.kind().name(),
                recommendation.headline(),
                recommendation.detail(),
                recommendation.confidence().name(),
                Timestamp.from(at));
    }

    @Override
    public java.util.Optional<StoredRun> latestRun() {
        return jdbc
                .query(
                        "select * from analysis_run where produced_by = 'DISCOVERY' order by seq desc limit 1",
                        (rs, row) -> new StoredRun(
                                rs.getObject("id", UUID.class),
                                rs.getTimestamp("window_from").toInstant(),
                                rs.getTimestamp("window_to").toInstant(),
                                rs.getInt("brackets_read"),
                                rs.getInt("waits_read"),
                                rs.getString("reached_stage"),
                                rs.getTimestamp("started_at").toInstant(),
                                rs.getTimestamp("finished_at") == null
                                        ? null
                                        : rs.getTimestamp("finished_at").toInstant()))
                .stream()
                .findFirst();
    }

    @Override
    public List<StoredRecommendation> latestRecommendations() {
        return jdbc.query(
                """
                select r.*, f.sample_size as sample_size
                from analysis_recommendation r
                join analysis_finding f on f.id = r.finding_id
                where r.run_id = (select id from analysis_run where produced_by = 'DISCOVERY' order by seq desc limit 1)
                  and r.acted_on_at is null
                  and r.dismissed_at is null
                order by
                    case r.confidence
                        when 'STRONG' then 0
                        when 'WORTH_LOOKING' then 1
                        else 2
                    end,
                    f.sample_size desc
                """,
                (rs, row) -> new StoredRecommendation(
                        rs.getObject("id", UUID.class),
                        rs.getObject("run_id", UUID.class),
                        rs.getObject("finding_id", UUID.class),
                        rs.getString("kind"),
                        rs.getString("headline"),
                        rs.getString("detail"),
                        rs.getString("confidence"),
                        rs.getInt("sample_size"),
                        rs.getTimestamp("created_at").toInstant()));
    }

    @Override
    public java.util.Optional<ProposedAction> proposal(UUID recommendationId) {
        return jdbc
                .query(
                        """
                        select r.id, r.run_id, r.kind, r.headline,
                               f.id as finding_id, f.subject_kind, f.subject_key, f.sample_size,
                               (r.acted_on_at is not null or r.dismissed_at is not null) as decided
                        from analysis_recommendation r
                        join analysis_finding f on f.id = r.finding_id
                        where r.id = ?
                        """,
                        (rs, row) -> new ProposedAction(
                                rs.getObject("id", UUID.class),
                                rs.getObject("run_id", UUID.class),
                                rs.getObject("finding_id", UUID.class),
                                rs.getString("kind"),
                                rs.getString("subject_kind"),
                                rs.getString("subject_key"),
                                rs.getString("headline"),
                                rs.getInt("sample_size"),
                                rs.getBoolean("decided")),
                        recommendationId)
                .stream()
                .findFirst();
    }

    @Override
    public boolean claim(UUID recommendationId, UUID by, Instant at) {
        return jdbc.update(
                        """
                        update analysis_recommendation
                           set acted_on_at = ?, acted_on_by = ?
                         where id = ? and acted_on_at is null and dismissed_at is null
                        """,
                        Timestamp.from(at),
                        by,
                        recommendationId)
                == 1;
    }

    @Override
    public void recordProduced(UUID recommendationId, UUID processTemplateId) {
        jdbc.update(
                "update analysis_recommendation set produced_template_id = ? where id = ?",
                processTemplateId,
                recommendationId);
    }

    @Override
    public boolean dismiss(UUID recommendationId, UUID by, Instant at) {
        return jdbc.update(
                        """
                        update analysis_recommendation
                           set dismissed_at = ?, dismissed_by = ?
                         where id = ? and acted_on_at is null and dismissed_at is null
                        """,
                        Timestamp.from(at),
                        by,
                        recommendationId)
                == 1;
    }

    @Override
    public void finishRun(UUID runId, Instant finishedAt, Stage reached) {
        jdbc.update(
                "update analysis_run set finished_at = ?, reached_stage = ? where id = ?",
                Timestamp.from(finishedAt),
                reached.name(),
                runId);
    }

    @Override
    public List<StoredFinding> latestFindings() {
        return jdbc.query(
                """
                select f.* from analysis_finding f
                where f.run_id = (select id from analysis_run where produced_by = 'DISCOVERY' order by seq desc limit 1)
                order by f.sample_size desc, f.created_at
                """,
                AnalysisStoreAdapter::readFinding);
    }

    @Override
    public List<StoredFinding> findingsTouching(UUID bracketId) {
        return jdbc.query(
                """
                select f.* from analysis_finding f
                join finding_subject s on s.finding_id = f.id
                where s.subject_id = ? and s.subject_kind = 'BRACKET'
                  and f.run_id = (select id from analysis_run where produced_by = 'DISCOVERY' order by seq desc limit 1)
                order by f.sample_size desc
                """,
                AnalysisStoreAdapter::readFinding,
                bracketId);
    }

    @Override
    public List<UUID> findingsOf(UUID findingId) {
        return jdbc.queryForList(
                "select subject_id from finding_subject where finding_id = ? and subject_kind = 'BRACKET'",
                UUID.class,
                findingId);
    }

    private static StoredFinding readFinding(ResultSet rs, int row) throws SQLException {
        return new StoredFinding(
                rs.getObject("id", UUID.class),
                rs.getObject("run_id", UUID.class),
                rs.getString("detector"),
                rs.getString("stage"),
                rs.getString("subject_kind"),
                rs.getString("subject_key"),
                rs.getString("headline"),
                rs.getString("phrasing"),
                rs.getString("phrased_by"),
                rs.getInt("sample_size"),
                rs.getBigDecimal("measure"),
                rs.getString("measure_unit"),
                rs.getTimestamp("created_at").toInstant());
    }
}
