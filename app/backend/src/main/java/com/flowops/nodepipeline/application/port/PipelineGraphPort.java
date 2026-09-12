package com.flowops.nodepipeline.application.port;

import com.flowops.nodepipeline.domain.CandidateTemplate;
import com.flowops.nodepipeline.domain.PipelineNode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface PipelineGraphPort {
    List<PipelineNode> nodesMarkedBetween(Instant from, Instant to, int limit);

    java.util.Map<String, Integer> nodeCountsByJobBetween(Instant from, Instant to);

    List<CandidateTemplate> library();

    /**
     * Every template the pipeline itself drew out of the graph, drafts included.
     *
     * <p>Distinct from {@link #library()}, which is approved entries only because those are the
     * only ones a run may match against. This is what the pipeline has proposed, which is a
     * different question and the one the guidance page asks.
     */
    List<CandidateTemplate> discoveredTemplates();

    /**
     * Every task template's title, by id.
     *
     * <p>A {@code ProcessShape}'s steps are template <em>ids</em>, which is what the matcher needs
     * and the last thing a person should be shown: handed the raw ids, a model wrote guidance
     * telling a new starter to "write down the first ID, then the second".
     */
    java.util.Map<String, String> templateTitles();

    /**
     * How many marks name an activity, out of how many there are.
     *
     * <p>The single figure that predicts step resolution better than any tuning does. If it sits
     * low, no amount of clustering compensates, because the finer key is simply not there to read.
     * Bracket closures are excluded: a closure inherits its bracket's activity rather than being
     * a person's choice, and counting it would report an adoption nobody performed.
     */
    Adoption activityAdoption();

    record Adoption(long marks, long naming) {
        public double share() {
            return marks == 0 ? 0 : (double) naming / marks;
        }
    }

    Optional<String> commonestWorkTypeOf(UUID performer);

    Set<String> directConversations();

    List<com.flowops.nodepipeline.domain.job.PipelineJob> jobsFor(java.util.Collection<String> jobIds);

    List<com.flowops.nodepipeline.domain.job.ProcessShape> processShapes();
}
