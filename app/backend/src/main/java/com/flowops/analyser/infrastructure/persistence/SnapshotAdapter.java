package com.flowops.analyser.infrastructure.persistence;

import com.flowops.analyser.application.shared.port.SnapshotPort;
import com.flowops.analyser.domain.Snapshot;
import com.flowops.nodepipeline.application.DiscoverShapesUseCase;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SnapshotAdapter implements SnapshotPort {
    private final JdbcTemplate jdbc;

    private final DiscoverShapesUseCase shapes;

    public SnapshotAdapter(JdbcTemplate jdbc, DiscoverShapesUseCase shapes) {
        this.jdbc = jdbc;
        this.shapes = shapes;
    }

    @Override
    public Snapshot readWindow(Instant from, Instant to) {
        return new Snapshot(
                from,
                to,
                nodes(from, to),
                brackets(from, to),
                jobs(from, to),
                waits(from, to),
                templates(),
                shapesIn(from, to),
                governedWorkTypes(),
                mergedActivities());
    }

    /**
     * The activities a person merged away, with the one each became.
     *
     * <p>Read here rather than passed in, because an analysis is a photograph of the workspace and
     * this is part of the workspace. Only MERGED rows whose survivor still exists are returned: a
     * pointer to nothing would produce a finding nobody could act on.
     */
    private List<Snapshot.MergedActivity> mergedActivities() {
        return jdbc.query(
                """
                select gone.name as name, kept.name as surviving
                  from activity gone
                  join activity kept on kept.id = gone.merged_into_id
                 where gone.status = 'MERGED'
                 order by gone.name
                """,
                (row, index) -> new Snapshot.MergedActivity(row.getString("name"), row.getString("surviving")));
    }

    private List<String> governedWorkTypes() {
        return jdbc.queryForList("select code from work_type_vocabulary", String.class);
    }

    private static final String NODES =
            """
            select id, job_id, work_type, title, detail, text, state, output_type,
                   performer_role_id, created_at, closed_at
              from work_node
             where created_at >= ? and created_at < ?
            """;

    private static final String TRAIL =
            """
            select t.node_id, t.from_state, t.to_state, t.actor_user_id, t.occurred_at
              from work_node_state_transition t
              join work_node n on n.id = t.node_id
             where n.created_at >= ? and n.created_at < ?
             order by t.node_id, t.occurred_at, t.seq
            """;

    private Map<String, List<Snapshot.Move>> movesByNode(Instant from, Instant to) {
        Map<String, List<Snapshot.Move>> moves = new HashMap<>();
        jdbc.query(
                TRAIL,
                row -> {
                    moves.computeIfAbsent(row.getString("node_id"), key -> new ArrayList<>())
                            .add(new Snapshot.Move(
                                    row.getString("from_state"),
                                    row.getString("to_state"),
                                    row.getString("actor_user_id"),
                                    instant(row, "occurred_at")));
                },
                Timestamp.from(from),
                Timestamp.from(to));
        return moves;
    }

    private List<Snapshot.Node> nodes(Instant from, Instant to) {
        Map<String, List<Snapshot.Move>> moves = movesByNode(from, to);
        return jdbc.query(
                NODES,
                (row, index) -> {
                    String id = row.getString("id");
                    return new Snapshot.Node(
                            id,
                            row.getString("job_id"),
                            row.getString("work_type"),
                            row.getString("title"),
                            row.getString("detail"),
                            row.getString("text"),
                            row.getString("state"),
                            row.getString("output_type"),
                            row.getString("performer_role_id"),
                            instant(row, "created_at"),
                            instant(row, "closed_at"),
                            moves.getOrDefault(id, List.of()));
                },
                Timestamp.from(from),
                Timestamp.from(to));
    }

    private static final String BRACKETS =
            """
            select id, job_id, work_type, close_kind, output_kind, opened_at, closed_at
              from work_bracket
             where opened_at >= ? and opened_at < ?
            """;

    private List<Snapshot.Bracket> brackets(Instant from, Instant to) {
        return jdbc.query(
                BRACKETS,
                (row, index) -> new Snapshot.Bracket(
                        row.getString("id"),
                        row.getString("job_id"),
                        row.getString("work_type"),
                        row.getString("close_kind"),
                        row.getString("output_kind"),
                        instant(row, "opened_at"),
                        instant(row, "closed_at")),
                Timestamp.from(from),
                Timestamp.from(to));
    }

    private static final String JOBS =
            """

            select j.id, j.name, j.status, j.project_label, c.name as counterparty_name,
                   j.opened_at, j.closed_at
              from job j
              left join counterparty c on c.id = j.counterparty_id
             where j.opened_at >= ? and j.opened_at < ?
            """;

    private List<Snapshot.Job> jobs(Instant from, Instant to) {
        return jdbc.query(
                JOBS,
                (row, index) -> new Snapshot.Job(
                        row.getString("id"),
                        row.getString("name"),
                        row.getString("status"),
                        row.getString("counterparty_name"),
                        row.getString("project_label"),
                        instant(row, "opened_at"),
                        instant(row, "closed_at")),
                Timestamp.from(from),
                Timestamp.from(to));
    }

    private static final String WAITS =
            """
            select id, bracket_id, kind, opened_at, satisfied_at, cancelled_at
              from work_node_wait
             where opened_at >= ? and opened_at < ?
            """;

    private List<Snapshot.Wait> waits(Instant from, Instant to) {
        return jdbc.query(
                WAITS,
                (row, index) -> new Snapshot.Wait(
                        row.getString("id"),
                        row.getString("bracket_id"),
                        row.getString("kind"),
                        instant(row, "opened_at"),
                        instant(row, "satisfied_at"),
                        instant(row, "cancelled_at")),
                Timestamp.from(from),
                Timestamp.from(to));
    }

    private static final String TEMPLATES =
            """
            select id, title, work_type, status, times_used
              from task_template
             where status = 'APPROVED'
            """;

    private List<Snapshot.Template> templates() {
        return jdbc.query(
                TEMPLATES,
                (row, index) -> new Snapshot.Template(
                        row.getString("id"),
                        row.getString("title"),
                        row.getString("work_type"),
                        row.getString("status"),
                        row.getInt("times_used")));
    }

    private Snapshot.Shapes shapesIn(Instant from, Instant to) {
        DiscoverShapesUseCase.Shapes found = shapes.shapesIn(from, to);

        return new Snapshot.Shapes(
                found.read(),
                found.excluded(),
                found.kinds().stream()
                        .map(kind -> new Snapshot.Shape(
                                kind.id(),
                                kind.workType(),
                                kind.subprocess(),
                                kind.nodeIds(),
                                kind.jobIds(),
                                kind.cohesion(),
                                kind.certainty(),
                                kind.activityName()))
                        .toList(),
                found.processes().stream()
                        .map(process -> new Snapshot.Process(
                                process.steps(),
                                process.order(),
                                process.orderReliable(),
                                process.orderConfidence(),
                                process.jobIds(),
                                process.runs(),
                                process.certainty()))
                        .toList());
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        Timestamp value = row.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
