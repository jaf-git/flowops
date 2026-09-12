package com.flowops.discovery.infrastructure.persistence;

import com.flowops.discovery.application.canvas.CanvasReadPort;
import com.flowops.discovery.domain.enums.CloseReason;
import com.flowops.discovery.domain.enums.Completeness;
import com.flowops.discovery.domain.enums.Direction;
import com.flowops.discovery.domain.enums.LoopExit;
import com.flowops.discovery.domain.enums.NodeKind;
import com.flowops.discovery.domain.enums.OutputType;
import com.flowops.discovery.domain.enums.PhaseKind;
import com.flowops.discovery.domain.enums.TrackState;
import java.sql.Array;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CanvasReadAdapter implements CanvasReadPort {
    private final JdbcTemplate jdbc;

    public CanvasReadAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String JOB_HEADER = "select id, name from job where id = ?";

    @Override
    public Optional<JobHeader> jobHeader(UUID jobId) {
        return jdbc
                .query(
                        JOB_HEADER,
                        (row, index) -> new JobHeader(row.getObject("id", UUID.class), row.getString("name")),
                        jobId)
                .stream()
                .findFirst();
    }

    private static final String LANES_OF_JOB =
            """
            select t.id                          as track_id,
                   asked.name                    as from_role_name,
                   doing.name                    as to_role_name,
                   t.state                       as state,
                   t.completeness                as completeness,
                   t.close_reason                as close_reason,
                   (t.key_basis = 'PERFORMER')   as weakly_keyed
            from track t
            left join functional_role asked on asked.id = t.from_role_id
            left join functional_role doing on doing.id = t.to_role_id
            where t.job_id = ?
            order by t.opened_at, t.id
            """;

    @Override
    public List<LaneRow> lanesOf(UUID jobId) {
        return jdbc.query(
                LANES_OF_JOB,
                (row, index) -> new LaneRow(
                        row.getObject("track_id", UUID.class),
                        row.getString("from_role_name"),
                        row.getString("to_role_name"),
                        TrackState.valueOf(row.getString("state")),
                        enumOrNull(Completeness.class, row.getString("completeness")),
                        enumOrNull(CloseReason.class, row.getString("close_reason")),
                        row.getBoolean("weakly_keyed")),
                jobId);
    }

    private static final String CARDS_OF_JOB =
            """
            select n.track_id                          as track_id,
                   n.id                                as node_id,
                   n.text                              as title,
                   n.kind                              as kind,
                   n.direction                         as direction,
                   n.output_type                       as output_type,
                   (n.task_template_id is not null)    as templated,
                   said.conversation_id                as conversation_id,
                   said.message_id                     as message_id
            from work_node n
            join track t on t.id = n.track_id and t.job_id = n.job_id
            left join lateral (
                select e.message_id, m.conversation_id
                from work_node_evidence e
                join message m on m.id = e.message_id
                where e.work_node_id = n.id
                  and e.origin in ('PRIMARY', 'SPLIT')
                order by e.added_at, e.id
                limit 1
            ) said on true
            where n.job_id = ?
            order by n.created_at, n.id
            """;

    @Override
    public List<CardRow> cardsOf(UUID jobId) {
        return jdbc.query(
                CARDS_OF_JOB,
                (row, index) -> new CardRow(
                        row.getObject("track_id", UUID.class),
                        row.getObject("node_id", UUID.class),
                        row.getString("title"),
                        NodeKind.valueOf(row.getString("kind")),
                        Direction.valueOf(row.getString("direction")),
                        enumOrNull(OutputType.class, row.getString("output_type")),
                        row.getBoolean("templated"),
                        row.getObject("conversation_id", UUID.class),
                        row.getObject("message_id", UUID.class)),
                jobId);
    }

    private static final String PHASES_OF_JOB =
            """
            select p.work_node_id                                                  as node_id,
                   p.phase                                                         as phase,
                   (extract(epoch from (p.ended_at - p.started_at)) * 1000)::bigint as ms
            from node_phase_row p
            join work_node n on n.id = p.work_node_id
            join track t on t.id = n.track_id and t.job_id = n.job_id
            where n.job_id = ?
              and p.ended_at is not null
            order by p.work_node_id, p.started_at, p.id
            """;

    @Override
    public List<PhaseRow> phasesOf(UUID jobId) {
        return jdbc.query(
                PHASES_OF_JOB,
                (row, index) -> new PhaseRow(
                        row.getObject("node_id", UUID.class),
                        PhaseKind.valueOf(row.getString("phase")),
                        row.getLong("ms")),
                jobId);
    }

    private static final String LOOPS_OF_JOB =
            """
            select l.id              as loop_id,
                   l.member_node_ids as member_node_ids,
                   l.cycle_count     as cycle_count,
                   l.exit_condition  as exit_condition,

                   (select m.track_id
                    from work_node m
                    where m.id = any (l.member_node_ids)
                      and m.job_id = l.job_id
                      and m.track_id is not null
                    limit 1) as track_id
            from loop l
            where l.job_id = ?
              and (select count(distinct m.track_id)
                   from work_node m
                   where m.id = any (l.member_node_ids)
                     and m.job_id = l.job_id) = 1
            order by l.id
            """;

    @Override
    public List<LoopRow> loopsOf(UUID jobId) {
        return jdbc.query(
                LOOPS_OF_JOB,
                (row, index) -> new LoopRow(
                        row.getObject("track_id", UUID.class),
                        members(row.getArray("member_node_ids")),
                        row.getInt("cycle_count"),
                        enumOrNull(LoopExit.class, row.getString("exit_condition"))),
                jobId);
    }

    private static List<UUID> members(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        return List.of((UUID[]) array.getArray());
    }

    private static <E extends Enum<E>> E enumOrNull(Class<E> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }
}
