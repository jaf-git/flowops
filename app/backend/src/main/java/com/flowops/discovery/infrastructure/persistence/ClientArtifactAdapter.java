package com.flowops.discovery.infrastructure.persistence;

import com.flowops.discovery.application.shared.port.ClientArtifactPort;
import com.flowops.discovery.domain.model.JobId;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ClientArtifactAdapter implements ClientArtifactPort {
    private final JdbcTemplate jdbc;

    public ClientArtifactAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Artifact> publishedBy(JobId job, UUID caller) {
        return jdbc.query(
                """
                select
                    a.id            as artifact_id,
                    a.bracket_id    as bracket_id,
                    b.work_type     as work_type,
                    a.kind          as kind,
                    a.created_at    as published_at,
                    case when may_read.ok then a.value end as value,
                    may_read.ok     as readable
                from client_artifact a
                join work_bracket b on b.id = a.bracket_id
                join job j on j.id = a.job_id
                cross join lateral (
                    select (
                        j.opened_by = ?
                        or exists (
                            select 1 from conversation_participant cp
                            where cp.conversation_id = a.conversation_id and cp.person_id = ?
                        )
                    ) as ok
                ) as may_read
                where a.job_id = ?
                order by a.created_at desc
                """,
                (rs, row) -> new Artifact(
                        rs.getObject("artifact_id", UUID.class),
                        rs.getObject("bracket_id", UUID.class),
                        rs.getString("work_type"),
                        rs.getString("kind"),
                        rs.getTimestamp("published_at").toInstant(),
                        rs.getString("value"),
                        rs.getBoolean("readable")),
                caller,
                caller,
                job.value());
    }
}
