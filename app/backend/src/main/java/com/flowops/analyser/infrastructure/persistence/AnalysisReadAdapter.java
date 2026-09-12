package com.flowops.analyser.infrastructure.persistence;

import com.flowops.analyser.application.shared.port.AnalysisReadPort;
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
public class AnalysisReadAdapter implements AnalysisReadPort {
    private final JdbcTemplate jdbc;

    public AnalysisReadAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String LATEST_RUN =
            """
            select id, window_from, window_to, started_at, finished_at, failure
              from analysis_run
             where reached_stage = 'ANALYSE'

               and produced_by = 'ANALYSER'
             order by started_at desc
             limit 1
            """;

    @Override
    public Optional<RunView> latest() {
        List<Object[]> runs = jdbc.query(LATEST_RUN, (row, index) -> new Object[] {
            row.getObject("id", UUID.class),
            instant(row, "window_from"),
            instant(row, "window_to"),
            instant(row, "started_at"),
            instant(row, "finished_at"),
            row.getString("failure")
        });
        if (runs.isEmpty()) {
            return Optional.empty();
        }

        Object[] run = runs.getFirst();
        UUID runId = (UUID) run[0];
        return Optional.of(new RunView(
                runId,
                (Instant) run[1],
                (Instant) run[2],
                (Instant) run[3],
                (Instant) run[4],
                (String) run[5],
                analysersOf(runId)));
    }

    private static final String REPORTS =
            """
            select analyser, items_read, findings_count, failure
              from analyser_report
             where run_id = ?
             order by analyser
            """;

    private static final String ABSENCES =
            """
            select analyser, what, detail, blocking
              from analyser_absence
             where run_id = ?
             order by analyser, what
            """;

    private static final String CLEAN =
            """
            select analyser, what, detail
              from analyser_clean
             where run_id = ?
             order by analyser, what
            """;

    private static final String PRECONDITIONS =
            """
            select analyser, needed, had, met, remedy
              from analyser_precondition
             where run_id = ?
             order by analyser, needed
            """;

    private List<AnalyserView> analysersOf(UUID runId) {
        Map<String, List<AbsenceView>> absences = new LinkedHashMap<>();

        jdbc.query(
                ABSENCES,
                row -> {
                    absences.computeIfAbsent(row.getString("analyser"), key -> new ArrayList<>())
                            .add(new AbsenceView(
                                    row.getString("what"), row.getString("detail"), row.getBoolean("blocking")));
                },
                runId);

        Map<String, List<CleanView>> clean = new LinkedHashMap<>();
        jdbc.query(
                CLEAN,
                row -> {
                    clean.computeIfAbsent(row.getString("analyser"), key -> new ArrayList<>())
                            .add(new CleanView(row.getString("what"), row.getString("detail")));
                },
                runId);

        Map<String, List<PreconditionView>> preconditions = new LinkedHashMap<>();
        jdbc.query(
                PRECONDITIONS,
                row -> {
                    preconditions
                            .computeIfAbsent(row.getString("analyser"), key -> new ArrayList<>())
                            .add(new PreconditionView(
                                    row.getString("needed"),
                                    row.getString("had"),
                                    row.getBoolean("met"),
                                    row.getString("remedy")));
                },
                runId);

        return jdbc.query(
                REPORTS,
                (row, index) -> {
                    String analyser = row.getString("analyser");
                    return new AnalyserView(
                            analyser,
                            row.getInt("items_read"),
                            row.getInt("findings_count"),
                            row.getString("failure"),
                            absences.getOrDefault(analyser, List.of()),
                            clean.getOrDefault(analyser, List.of()),
                            preconditions.getOrDefault(analyser, List.of()));
                },
                runId);
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        Timestamp value = row.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
