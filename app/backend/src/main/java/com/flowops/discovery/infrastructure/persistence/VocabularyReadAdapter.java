package com.flowops.discovery.infrastructure.persistence;

import com.flowops.discovery.application.shared.port.VocabularyReadPort;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class VocabularyReadAdapter implements VocabularyReadPort {
    private final JdbcTemplate jdbc;

    public VocabularyReadAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Familiarity familiarityOf(String workType) {
        String normalised = workType == null ? "" : workType.trim().toUpperCase(Locale.ROOT);

        Integer used =
                jdbc.queryForObject("select count(*) from work_bracket where work_type = ?", Integer.class, normalised);

        boolean neverUsedBefore = used == null || used == 0;

        List<String> closest = neverUsedBefore
                ? jdbc.queryForList(
                        """
                        select distinct work_type
                        from work_bracket
                        where work_type <> ?
                          and (work_type like left(?, 4) || '%' or ? like left(work_type, 4) || '%')
                        order by work_type
                        limit 5
                        """,
                        String.class, normalised, normalised, normalised)
                : List.of();

        return new Familiarity(normalised, neverUsedBefore, closest);
    }

    @Override
    public List<FirstUse> workTypesFirstUsedSince(Instant since) {
        return jdbc.query(
                """
                select 'WORK_TYPE' as kind, first.work_type as name, first.at as at,
                       b.closure_right as by_person,
                       coalesce(u.display_name, u.email, 'Former member') as by_name
                from (
                    select work_type, min(opened_at) as at
                    from work_bracket
                    where is_boundary = false
                    group by work_type
                ) first
                join work_bracket b on b.work_type = first.work_type and b.opened_at = first.at
                left join auth_user u on u.id = b.closure_right
                where first.at >= ?
                order by first.at
                """,
                VocabularyReadAdapter::readFirstUse,
                Timestamp.from(since));
    }

    @Override
    public List<FirstUse> counterpartiesAndProjectsFirstSeenSince(Instant since) {
        return jdbc.query(
                """
                select 'COUNTERPARTY' as kind, c.name as name, first.at as at,
                       null::uuid as by_person, null::text as by_name
                from (
                    select counterparty_id, min(opened_at) as at
                    from job
                    where counterparty_id is not null
                    group by counterparty_id
                ) first
                join counterparty c on c.id = first.counterparty_id
                where first.at >= ?

                union all

                select 'PROJECT' as kind, first.project_label as name, first.at as at,
                       null::uuid as by_person, null::text as by_name
                from (
                    select project_label, min(opened_at) as at
                    from job
                    where project_label is not null
                    group by project_label
                ) first
                where first.at >= ?

                order by at
                """,
                VocabularyReadAdapter::readFirstUse,
                Timestamp.from(since),
                Timestamp.from(since));
    }

    @Override
    public List<MergeSuggestion> typesWorthMerging(int rareBelow) {
        return jdbc.query(
                """
                with uses as (
                    select work_type, count(*) as times
                    from work_bracket
                    where is_boundary = false
                    group by work_type
                )
                select a.work_type as one, a.times as one_uses,
                       b.work_type as other, b.times as other_uses,
                       left(a.work_type, 4) as shared_prefix
                from uses a
                join uses b
                  on b.work_type > a.work_type
                 and left(b.work_type, 4) = left(a.work_type, 4)
                where a.times < ? and b.times < ?
                order by a.work_type
                """,
                (rs, row) -> new MergeSuggestion(
                        rs.getString("one"),
                        rs.getInt("one_uses"),
                        rs.getString("other"),
                        rs.getInt("other_uses"),
                        rs.getString("shared_prefix")),
                rareBelow,
                rareBelow);
    }

    private static FirstUse readFirstUse(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new FirstUse(
                rs.getString("kind"),
                rs.getString("name"),
                rs.getTimestamp("at").toInstant(),
                rs.getObject("by_person", UUID.class),
                rs.getString("by_name"));
    }
}
