package com.flowops.discovery.application.markintobracket;

import com.flowops.discovery.application.markmessage.UnknownJobException;
import com.flowops.discovery.application.shared.port.WorkBracketPort;
import com.flowops.discovery.domain.model.BracketAddress;
import com.flowops.discovery.domain.model.JobId;
import com.flowops.discovery.domain.model.WorkTypeCatalogue;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ComposeAddress {
    private final WorkBracketPort brackets;
    private final JdbcTemplate jdbc;

    public ComposeAddress(WorkBracketPort brackets, JdbcTemplate jdbc) {
        this.brackets = brackets;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public BracketAddress composeFor(JobId job, UUID conversationId, UUID performerId) {
        return composeFor(job, conversationId, performerId, null);
    }

    @Transactional(readOnly = true)
    public BracketAddress composeFor(JobId job, UUID conversationId, UUID performerId, String workTypeOverride) {
        Objects.requireNonNull(job, "a mark belongs to an engagement");
        Objects.requireNonNull(conversationId, "an address names the chat the work was spoken in");

        JobFacts facts = jobFacts(job);

        String corrected = workTypeOverride == null || workTypeOverride.isBlank()
                ? null
                : workTypeOverride.trim().toUpperCase(java.util.Locale.ROOT);

        String workType = corrected != null
                ? corrected
                : workTypeOpenHere(job, conversationId, performerId).orElseGet(() -> workTypeOf(performerId));

        return new BracketAddress(conversationId, facts.counterpartyId(), facts.projectLabel(), workType, performerId);
    }

    @Transactional(readOnly = true)
    public Destination previewFor(JobId job, UUID conversationId, UUID performerId) {
        return previewFor(job, conversationId, performerId, null);
    }

    @Transactional(readOnly = true)
    public Destination previewFor(JobId job, UUID conversationId, UUID performerId, String workTypeOverride) {
        BracketAddress address = composeFor(job, conversationId, performerId, workTypeOverride);

        return brackets.findOpenAt(job, address)
                .map(existing ->
                        new Destination(address.describe(), true, existing.id().value(), address.workType()))
                .orElseGet(() -> new Destination(address.describe(), false, null, address.workType()));
    }

    public record Destination(String describe, boolean joins, UUID bracketId, String workType) {}

    private java.util.Optional<String> workTypeOpenHere(JobId job, UUID conversationId, UUID performerId) {
        if (performerId == null) {
            return java.util.Optional.empty();
        }

        List<String> distinct = jdbc.queryForList(
                """
                select distinct work_type
                from work_bracket
                where job_id = ?
                  and conversation_id = ?
                  and performer_ref = ?
                  and state in ('OPEN', 'WAITING')
                  and is_boundary = false
                """,
                String.class,
                job.value(),
                conversationId,
                performerId);

        return distinct.size() == 1 ? java.util.Optional.of(distinct.get(0)) : java.util.Optional.empty();
    }

    private String workTypeOf(UUID performerId) {
        if (performerId == null) {
            return WorkTypeCatalogue.GENERAL;
        }

        return jdbc
                .query(
                        """
                        select r.name
                        from workspace_membership m
                        join functional_role r on r.id = m.functional_role_id
                        where m.user_id = ?
                        limit 1
                        """,
                        (rs, row) -> rs.getString("name"),
                        performerId)
                .stream()
                .findFirst()
                .map(WorkTypeCatalogue::forRole)
                .orElse(WorkTypeCatalogue.GENERAL);
    }

    private JobFacts jobFacts(JobId job) {
        return jdbc
                .query(
                        "select counterparty_id, project_label from job where id = ?",
                        (rs, row) -> new JobFacts(
                                rs.getObject("counterparty_id", UUID.class), rs.getString("project_label")),
                        job.value())
                .stream()
                .findFirst()
                .orElseThrow(() -> new UnknownJobException(job.value()));
    }

    private record JobFacts(UUID counterpartyId, String projectLabel) {}
}
