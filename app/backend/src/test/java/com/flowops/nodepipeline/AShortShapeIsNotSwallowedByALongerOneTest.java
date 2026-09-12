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

class AShortShapeIsNotSwallowedByALongerOneTest {
    private static final DiscoveryWeights WEIGHTS = DiscoveryWeights.reference();

    private static final int RUNS_EACH = 4;

    private static final List<String> SENTENCES = List.of(
            "shot the product set in the studio and backed the cards up",
            "wrote the summer menu copy and sent the draft over for review",
            "laid out the poster and the three social crops from it",
            "booked the flight for monday and told the client the dates");

    @Test
    @DisplayName("a three-step shape that shares two steps with a longer-lived one stays its own process")
    void aShortShapeIsNotAbsorbedByOneItSharesTwoStepsWith() {
        List<PipelineNode> nodes = new ArrayList<>();
        List<PipelineJob> jobs = new ArrayList<>();

        addShape(jobs, nodes, "job-a-retainer", new String[] {"WRITER", "DESIGN", "SCHEDULING"});
        addShape(jobs, nodes, "job-b-shoot", new String[] {"PHOTO", "DESIGN", "SCHEDULING"});

        List<StepKind> kinds = new StepDiscovery(WEIGHTS).discover(nodes, Set.of());

        assertThat(kinds).as("four governed types, each on at least four nodes").hasSize(4);

        List<DiscoveredProcess> processes = new ProcessDiscovery(WEIGHTS).discover(jobs, nodes, kinds);

        assertThat(processes)
                .as("two shapes differing in exactly one step are two processes, not one")
                .hasSize(2);
        assertThat(processes)
                .extracting(DiscoveredProcess::runs)
                .as("neither swallowed the other — a cluster of eight is the defect this test exists for")
                .containsExactly(RUNS_EACH, RUNS_EACH);
    }

    @Test
    @DisplayName("the step that distinguishes the shorter shape survives into a process")
    void theDistinguishingStepIsStillInAProcess() {
        List<PipelineNode> nodes = new ArrayList<>();
        List<PipelineJob> jobs = new ArrayList<>();
        addShape(jobs, nodes, "job-a-retainer", new String[] {"WRITER", "DESIGN", "SCHEDULING"});
        addShape(jobs, nodes, "job-b-shoot", new String[] {"PHOTO", "DESIGN", "SCHEDULING"});

        List<StepKind> kinds = new StepDiscovery(WEIGHTS).discover(nodes, Set.of());
        List<DiscoveredProcess> processes = new ProcessDiscovery(WEIGHTS).discover(jobs, nodes, kinds);

        assertThat(processes)
                .as("PHOTO is what SHOOT has and RETAINER does not; absorbed, it disappears entirely")
                .anyMatch(process -> process.core().stream().anyMatch(step -> step.contains("PHOTO")));
    }

    private static void addShape(List<PipelineJob> jobs, List<PipelineNode> nodes, String prefix, String[] types) {
        for (int engagement = 0; engagement < RUNS_EACH; engagement++) {
            String jobId = prefix + "-" + engagement;
            jobs.add(finishedJob(jobId));
            for (int step = 0; step < types.length; step++) {
                nodes.add(node(jobId + "-n" + step, jobId, types[step], SENTENCES.get(step % SENTENCES.size())));
            }
        }
    }

    private static PipelineJob finishedJob(String id) {
        return new PipelineJob(
                id, "Engagement " + id, "CLOSED", false, true, false, null, "CLIENT", LocalDate.of(2026, 9, 20));
    }

    private static PipelineNode node(String id, String jobId, String workType, String text) {
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
                workType,
                null,
                false,
                UUID.randomUUID().toString(),
                null,
                null,
                null,
                null);
    }
}
