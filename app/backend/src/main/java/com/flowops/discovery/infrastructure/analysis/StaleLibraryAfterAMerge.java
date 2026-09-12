package com.flowops.discovery.infrastructure.analysis;

import com.flowops.analyser.application.vocabulary.RaiseMergeLeftoversUseCase;
import com.flowops.discovery.application.shared.port.StaleLibraryPort;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads what a merge left in the library, and hands it to the analyser to raise.
 *
 * <p><b>Nothing about a finding is decided here.</b> Discovery may call another feature's use cases
 * and may never reach into its domain, which {@code ArchitectureRulesTest} enforces by name — so
 * this passes titles, identifiers and counts, and the analyser decides what a finding made of them
 * looks like. That boundary is why the raising lives one call away rather than inline.
 */
@Component
public class StaleLibraryAfterAMerge implements StaleLibraryPort {
    private final JdbcTemplate jdbc;
    private final RaiseMergeLeftoversUseCase raise;

    public StaleLibraryAfterAMerge(JdbcTemplate jdbc, RaiseMergeLeftoversUseCase raise) {
        this.jdbc = jdbc;
        this.raise = raise;
    }

    /**
     * Matches on the title exactly, folded for case, because that is all the schema knows.
     *
     * <p>{@code Conversion.titleFor} names a drafted template with the activity a person chose and
     * nothing else, and no column joins the two — implementer decision 141, deliberately. A looser
     * match would name templates the merge never touched, and a finding whose evidence is wrong is
     * worse than no finding at all.
     */
    @Override
    public List<StaleTemplate> approvedTemplatesNaming(String activityName) {
        return jdbc.query(
                """
                select id, title, times_used
                  from task_template
                 where status = 'APPROVED'
                   and lower(title) = lower(?)
                 order by times_used desc, title
                """,
                (row, index) -> new StaleTemplate(
                        row.getObject("id", UUID.class), row.getString("title"), row.getInt("times_used")),
                activityName);
    }

    @Override
    public void templatesLeftBehindByAMerge(
            String absorbedActivity, String survivingActivity, List<StaleTemplate> templates, Instant at) {
        raise.execute(new RaiseMergeLeftoversUseCase.MergeLeftovers(
                absorbedActivity,
                survivingActivity,
                templates.stream()
                        .map(one -> new RaiseMergeLeftoversUseCase.Template(one.id(), one.title(), one.timesUsed()))
                        .toList(),
                at));
    }
}
