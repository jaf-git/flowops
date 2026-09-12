package com.flowops.analyser.infrastructure.persistence;

import com.flowops.analyser.application.shared.port.AnalysisJournalPort;
import com.flowops.analyser.domain.Absence;
import com.flowops.analyser.domain.Clean;
import com.flowops.analyser.domain.Precondition;
import com.flowops.analyser.domain.Report;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AnalysisJournalAdapter implements AnalysisJournalPort {
    private final JdbcTemplate jdbc;

    public AnalysisJournalAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String OPEN_RUN =
            """
            insert into analysis_run (
                id, window_from, window_to, started_at, brackets_read, waits_read, reached_stage, produced_by)
            values (?, ?, ?, ?, 0, 0, 'ANALYSE', 'ANALYSER')
            """;

    @Override
    public UUID open(Instant windowFrom, Instant windowTo, Instant startedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update(OPEN_RUN, id, Timestamp.from(windowFrom), Timestamp.from(windowTo), Timestamp.from(startedAt));
        return id;
    }

    private static final String RECORD_REPORT =
            """
            insert into analyser_report (run_id, analyser, items_read, findings_count, failure)
            values (?, ?, ?, ?, ?)
            on conflict (run_id, analyser) do update set
                items_read     = excluded.items_read,
                findings_count = excluded.findings_count,
                failure        = excluded.failure
            """;

    private static final String RECORD_ABSENCE =
            """
            insert into analyser_absence (id, run_id, analyser, what, detail, blocking)
            values (?, ?, ?, ?, ?, ?)
            """;

    private static final String RECORD_CLEAN =
            """
            insert into analyser_clean (id, run_id, analyser, what, detail)
            values (?, ?, ?, ?, ?)
            """;

    private static final String RECORD_PRECONDITION =
            """
            insert into analyser_precondition (id, run_id, analyser, needed, had, met, remedy)
            values (?, ?, ?, ?, ?, ?, ?)
            """;

    @Override
    public void record(UUID runId, Report report, String failure) {
        jdbc.update(
                RECORD_REPORT,
                runId,
                report.analyser(),
                report.read(),
                report.findings().size(),
                failure);

        for (Absence absence : report.absences()) {
            jdbc.update(
                    RECORD_ABSENCE,
                    UUID.randomUUID(),
                    runId,
                    report.analyser(),
                    absence.what(),
                    absence.detail(),
                    absence.blocking());
        }

        for (Clean clean : report.clean()) {
            jdbc.update(RECORD_CLEAN, UUID.randomUUID(), runId, report.analyser(), clean.what(), clean.detail());
        }

        for (Precondition precondition : report.preconditions()) {
            jdbc.update(
                    RECORD_PRECONDITION,
                    UUID.randomUUID(),
                    runId,
                    report.analyser(),
                    precondition.needed(),
                    precondition.had(),
                    precondition.met(),
                    precondition.remedy());
        }
    }

    private static final String FINISH_RUN = "update analysis_run set finished_at = ? where id = ?";

    @Override
    public void finish(UUID runId, Instant finishedAt) {
        jdbc.update(FINISH_RUN, Timestamp.from(finishedAt), runId);
    }

    private static final String FAIL_RUN = "update analysis_run set finished_at = ?, failure = ? where id = ?";

    @Override
    public void fail(UUID runId, Instant finishedAt, String failure) {
        jdbc.update(FAIL_RUN, Timestamp.from(finishedAt), failure, runId);
    }
}
