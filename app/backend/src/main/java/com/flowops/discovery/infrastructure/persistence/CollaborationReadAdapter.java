package com.flowops.discovery.infrastructure.persistence;

import com.flowops.discovery.application.shared.port.CollaborationReadPort;
import com.flowops.discovery.domain.model.JobId;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CollaborationReadAdapter implements CollaborationReadPort {
    private final JdbcTemplate jdbc;

    public CollaborationReadAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Pairing> declaredJoins(JobId job) {
        return jdbc.query(
                """
                select least(i.joiner_bracket_id, i.joined_bracket_id)    as one_bracket,
                       greatest(i.joiner_bracket_id, i.joined_bracket_id) as other_bracket,
                       joiner.work_type                                   as work_type,
                       'declared'                                         as evidence
                from bracket_join_intent i
                join work_bracket joiner on joiner.id = i.joiner_bracket_id
                join work_bracket joined on joined.id = i.joined_bracket_id
                where i.job_id = ?
                  and joiner.is_boundary = false and joined.is_boundary = false
                  and joiner.disrupted = false and joined.disrupted = false
                """,
                CollaborationReadAdapter::readPairing,
                job.value());
    }

    @Override
    public List<Pairing> sharingAnOutput(JobId job) {
        return jdbc.query(
                """
                select b.id as one_bracket, o.id as other_bracket, b.work_type as work_type,
                       b.output_value as evidence
                from work_bracket b
                join work_bracket o
                  on o.job_id = b.job_id
                 and o.output_value = b.output_value
                 and o.performer_ref is distinct from b.performer_ref
                 and b.id < o.id
                where b.job_id = ?
                  and b.output_value is not null
                  and b.close_kind in ('DELIVERED', 'DONE')
                  and o.close_kind in ('DELIVERED', 'DONE')
                  and b.is_boundary = false and o.is_boundary = false
                  and b.disrupted = false and o.disrupted = false
                """,
                CollaborationReadAdapter::readPairing,
                job.value());
    }

    @Override
    public List<Pairing> askedForTogether(JobId job) {
        return jdbc.query(
                """
                select b.id as one_bracket, o.id as other_bracket, b.work_type as work_type,
                       be.message_id::text as evidence
                from work_bracket b
                join work_node_evidence be
                  on be.work_node_id = b.opened_by_node and be.origin in ('PRIMARY', 'SPLIT')
                join work_node_evidence oe
                  on oe.message_id = be.message_id and oe.origin in ('PRIMARY', 'SPLIT')
                join work_bracket o
                  on o.opened_by_node = oe.work_node_id
                 and o.job_id = b.job_id
                 and o.work_type = b.work_type
                 and o.performer_ref is distinct from b.performer_ref
                 and b.id < o.id
                where b.job_id = ?
                  and b.is_boundary = false and o.is_boundary = false
                  and b.disrupted = false and o.disrupted = false
                """,
                CollaborationReadAdapter::readPairing,
                job.value());
    }

    @Override
    public List<Pairing> workingAlongside(JobId job) {
        return jdbc.query(
                """
                select b.id as one_bracket, o.id as other_bracket, b.work_type as work_type,
                       b.work_type as evidence
                from work_bracket b
                join work_bracket o
                  on o.job_id = b.job_id
                 and o.work_type = b.work_type
                 and o.performer_ref is distinct from b.performer_ref
                 and b.id < o.id
                where b.job_id = ?
                  and b.is_boundary = false and o.is_boundary = false
                  and b.disrupted = false and o.disrupted = false
                  and not (
                      b.output_value is not null
                      and b.output_value = o.output_value
                      and b.close_kind in ('DELIVERED','DONE')
                      and o.close_kind in ('DELIVERED','DONE')
                  )
                  and not exists (
                      select 1
                      from work_node_evidence be
                      join work_node_evidence oe
                        on oe.message_id = be.message_id and oe.origin in ('PRIMARY', 'SPLIT')
                      where be.work_node_id = b.opened_by_node
                        and be.origin in ('PRIMARY', 'SPLIT')
                        and oe.work_node_id = o.opened_by_node
                  )
                """,
                CollaborationReadAdapter::readPairing,
                job.value());
    }

    @Override
    public List<Reuse> assetsReusedAcrossJobs() {
        return jdbc.query(
                """
                select b.output_value as value,
                       b.job_id as first_job, o.job_id as second_job,
                       b.id as first_bracket, o.id as second_bracket
                from work_bracket b
                join work_bracket o
                  on o.output_value = b.output_value
                 and o.output_kind = b.output_kind
                 and o.job_id <> b.job_id
                 and b.id < o.id
                where b.output_value is not null
                  and b.close_kind in ('DELIVERED', 'DONE')
                  and o.close_kind in ('DELIVERED', 'DONE')
                  and b.output_kind in ('LINK', 'MESSAGE_REF')
                order by b.output_value
                """,
                (rs, row) -> new Reuse(
                        rs.getString("value"),
                        rs.getObject("first_job", UUID.class),
                        rs.getObject("second_job", UUID.class),
                        rs.getObject("first_bracket", UUID.class),
                        rs.getObject("second_bracket", UUID.class)));
    }

    private static Pairing readPairing(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Pairing(
                rs.getObject("one_bracket", UUID.class),
                rs.getObject("other_bracket", UUID.class),
                rs.getString("work_type"),
                rs.getString("evidence"));
    }
}
