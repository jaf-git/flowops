package com.flowops.nodepipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.nodepipeline.domain.PipelineNode;
import com.flowops.nodepipeline.domain.discovery.DiscoveredProcess;
import com.flowops.nodepipeline.domain.discovery.DiscoveryWeights;
import com.flowops.nodepipeline.domain.discovery.ProcessDiscovery;
import com.flowops.nodepipeline.domain.discovery.StepDiscovery;
import com.flowops.nodepipeline.domain.discovery.StepKind;
import com.flowops.nodepipeline.domain.job.PipelineJob;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TheStepKeyFollowsTheActivityWhenSomebodyNamedOneTest {
    private static final DiscoveryWeights WEIGHTS = DiscoveryWeights.reference();

    private static final List<String> SENTENCES = List.of(
            "wrote the summer menu copy and sent the draft over for review",
            "design brief for the summer menu written up and handed to the studio",
            "caption for the summer menu post written with the hashtags");

    @Test
    @DisplayName("a corpus where nobody named an activity produces exactly the identifiers it produced before")
    void theIdentifierIsUnchangedWhereThereIsNoActivity() {
        List<PipelineNode> nodes = new ArrayList<>();
        for (int engagement = 0; engagement < 4; engagement++) {
            String jobId = "job-" + engagement;
            for (int step = 0; step < 3; step++) {
                nodes.add(unnamed(jobId + "-n" + step, jobId, SENTENCES.get(step)));
            }
        }

        List<StepKind> kinds = new StepDiscovery(WEIGHTS).discover(nodes, Set.of());

        assertThat(kinds.stream().map(StepKind::id))
                .as("the fallback path is the whole safety property: an unnamed corpus keys on the work type, "
                        + "byte for byte as it did before the column existed")
                .containsExactly("K:CONTENT");
        assertThat(kinds.getFirst().namesAnActivity()).isFalse();
    }

    @Test
    @DisplayName("three activities one person performed under one work type become three steps, not one")
    void oneWorkTypeCarryingThreeActivitiesBecomesThreeSteps() {
        List<PipelineNode> nodes = new ArrayList<>();
        List<PipelineJob> jobs = new ArrayList<>();

        String[] slugs = {"write-the-content", "design-brief", "write-the-caption"};
        String[] names = {"Write the content", "Design brief", "Write the caption"};

        for (int engagement = 0; engagement < 4; engagement++) {
            String jobId = "job-" + engagement;
            jobs.add(finishedJob(jobId));
            for (int step = 0; step < slugs.length; step++) {
                nodes.add(named(jobId + "-n" + step, jobId, SENTENCES.get(step), slugs[step], names[step]));
            }
        }

        List<StepKind> kinds = new StepDiscovery(WEIGHTS).discover(nodes, Set.of());

        assertThat(kinds.stream().map(StepKind::id))
                .as("this is the defect the field exists to fix - the same three marks collapsed to one step "
                        + "when the key was the work type derived from Cristina's role")
                .containsExactlyInAnyOrder(
                        "K:CONTENT@write-the-content", "K:CONTENT@design-brief", "K:CONTENT@write-the-caption");

        assertThat(kinds)
                .as("the activity's name travels with the kind, so a reader is not shown the work type three times")
                .allSatisfy(kind -> assertThat(kind.bestName()).isNotEqualTo("CONTENT"));

        List<DiscoveredProcess> processes = new ProcessDiscovery(WEIGHTS).discover(jobs, nodes, kinds);

        assertThat(processes).hasSize(1);
        assertThat(processes.getFirst().core())
                .as("a three-activity process is three steps; keyed on work type it was one, and one step is "
                        + "below the core floor of two, so it was not a process at all")
                .hasSize(3);
    }

    @Test
    @DisplayName("a named activity and an unnamed mark of the same work type do not share a step")
    void anUnnamedMarkDoesNotJoinANamedOne() {
        List<PipelineNode> nodes = new ArrayList<>();
        for (int engagement = 0; engagement < 4; engagement++) {
            String jobId = "job-" + engagement;
            nodes.add(named(jobId + "-a", jobId, SENTENCES.get(0), "write-the-content", "Write the content"));
            nodes.add(unnamed(jobId + "-b", jobId, SENTENCES.get(2)));
        }

        assertThat(new StepDiscovery(WEIGHTS).discover(nodes, Set.of()).stream().map(StepKind::id))
                .as("mixed adoption keeps the two apart rather than pretending the unnamed mark is the named one")
                .containsExactlyInAnyOrder("K:CONTENT@write-the-content", "K:CONTENT");
    }

    private static PipelineJob finishedJob(String id) {
        return new PipelineJob(
                id, "Engagement " + id, "CLOSED", false, true, false, null, "CLIENT", LocalDate.of(2026, 9, 20));
    }

    private static PipelineNode unnamed(String id, String jobId, String text) {
        return node(id, jobId, text, null, null);
    }

    private static PipelineNode named(String id, String jobId, String text, String slug, String name) {
        return node(id, jobId, text, slug, name);
    }

    private static PipelineNode node(String id, String jobId, String text, String slug, String activityName) {
        return new PipelineNode(
                id,
                jobId,
                text,
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "WORK",
                "Account manager",
                "Content writer",
                LocalDate.of(2026, 9, 20),
                PipelineNode.Closure.MARKED,
                "REQUEST",
                false,
                null,
                "CONTENT",
                null,
                false,
                UUID.randomUUID().toString(),
                null,
                null,
                null,
                null,
                null,
                null,
                slug,
                activityName);
    }
}
