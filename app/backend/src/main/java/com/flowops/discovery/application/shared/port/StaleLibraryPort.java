package com.flowops.discovery.application.shared.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What a merge leaves behind in the library, and how a person is told about it.
 *
 * <p><b>Merging two activities never retires a template, and this port is why it does not have to.</b>
 * A template is APPROVED by a person and carries a use count; deciding that two words mean one thing
 * is not evidence that a task somebody wrote down has become wrong. {@code
 * DECISION-NO-TEMPLATE-BINDING-01} settles the direction: nothing binds instantiated work to its
 * template, so nothing here may infer that the library must follow the vocabulary.
 *
 * <p>So the merge does the one thing it is entitled to do — it says what it found, with the templates
 * as the evidence, and leaves the judgement where it belongs. This is the same shape every other
 * claim in this product takes: the model suggests and never merges, composition writes DRAFT and
 * never APPROVED, order is observed and never confirmed.
 *
 * <p>The link between a template and an activity is its <b>title</b> and nothing else. There is no
 * column joining them and there deliberately is not one — implementer decision 141 — so this reads
 * exactly as much as the schema actually knows, which is that a template named "Kickoff notes" was
 * drafted from work somebody called "Kickoff notes".
 */
public interface StaleLibraryPort {
    /**
     * The approved templates whose title is this activity's name.
     *
     * <p>Exact on the name, folded for case, because {@code Conversion.titleFor} names a drafted
     * template with the activity a person chose and nothing else. A looser match would report
     * templates the merge did not touch, and a finding that names the wrong evidence is worse than
     * no finding.
     */
    List<StaleTemplate> approvedTemplatesNaming(String activityName);

    /**
     * Raises the finding a person sees, with the templates as its evidence.
     *
     * <p>Fired at merge time rather than on the next scheduled run. The person who has just decided
     * that two names were one activity is the one who can say whether the templates drafted from the
     * absorbed name still describe real work; a week later they have forgotten which merge this was.
     *
     * <p>Does nothing where there is no analysis to attach it to, because a finding nobody can open
     * is not a finding. The workspace's first analysis will raise it in the ordinary way.
     */
    void templatesLeftBehindByAMerge(
            String absorbedActivity, String survivingActivity, List<StaleTemplate> templates, Instant at);

    record StaleTemplate(UUID id, String title, int timesUsed) {}
}
