package com.flowops.discovery.infrastructure.persistence;

import com.flowops.discovery.application.shared.port.JobHeaderPort;
import com.flowops.discovery.domain.model.JobId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JobHeaderAdapter implements JobHeaderPort {
    private final JdbcTemplate jdbc;

    public JobHeaderAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<JobHeader> headerOf(JobId job) {
        List<JobHeader> found = jdbc.query(
                """
                select
                    j.id                as job_id,
                    j.name              as name,
                    c.name              as client,
                    j.project_label     as project,

                    case j.status
                        when 'FORCE_CLOSED' then 'CLOSED'
                        when 'AUTO_CLOSED'  then 'CLOSED'
                        else j.status
                    end                 as status,
                    coalesce(closer.display_name, closer.email, owner.display_name, owner.email)
                                        as closer_name,
                    (
                        select count(*)
                        from work_bracket live
                        where live.job_id = j.id
                          and live.is_boundary = false
                          and live.state in ('OPEN', 'WAITING')
                    )                   as live_brackets,

                    (
                        select count(*)
                        from work_bracket ever
                        where ever.job_id = j.id
                          and ever.is_boundary = false
                    )                   as total_brackets,
                    j.close_reason      as close_reason,
                    j.shape_eligible    as shape_eligible
                from job j
                left join counterparty c on c.id = j.counterparty_id
                left join auth_user owner on owner.id = j.opened_by
                left join work_bracket boundary
                       on boundary.job_id = j.id and boundary.is_boundary = true
                left join auth_user closer on closer.id = boundary.closure_right
                where j.id = ?
                """,
                (rs, row) -> new JobHeader(
                        rs.getObject("job_id", UUID.class),
                        rs.getString("name"),
                        rs.getString("client"),
                        rs.getString("project"),
                        rs.getString("status"),
                        rs.getString("closer_name"),
                        rs.getInt("live_brackets"),
                        rs.getInt("total_brackets"),
                        rs.getString("close_reason"),
                        rs.getBoolean("shape_eligible")),
                job.value());

        return found.stream().findFirst();
    }
}
