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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EveryDiscoveredProcessIsMadeOfOneShapeTest {
    private static final DiscoveryWeights WEIGHTS = DiscoveryWeights.reference();

    private static final List<String> SENTENCES = List.of(
            "shot the product set in the studio and backed the cards up",
            "wrote the summer menu copy and sent the draft over for review",
            "laid out the poster and the three social crops from it",
            "booked the flight for monday and told the client the dates",
            "pulled the numbers for the monthly report and checked them twice");

    @Test
    @DisplayName("two shapes sharing two of three steps never end up inside one process")
    void twoShapesSharingTwoStepsAreNeverMixedIntoOneProcess() {
        List<PipelineNode> nodes = new ArrayList<>();
        List<PipelineJob> jobs = new ArrayList<>();
        addShape(jobs, nodes, "job-a-retainer", 6, "WRITER", "DESIGN", "SCHEDULING");
        addShape(jobs, nodes, "job-b-shoot", 7, "PHOTO", "DESIGN", "SCHEDULING");

        List<DiscoveredProcess> processes = discover(jobs, nodes);

        assertThat(processes).as("two shapes, two processes").hasSize(2);
        assertThat(processes)
                .extracting(DiscoveredProcess::runs)
                .as("six and seven, neither absorbed into the other")
                .containsExactlyInAnyOrder(6, 7);
        assertEveryProcessHoldsOneShape(processes);
    }

    @Test
    @DisplayName("three long shapes each sharing four of five steps stay three processes")
    void longShapesThatOverlapHeavilyStayApart() {
        List<PipelineNode> nodes = new ArrayList<>();
        List<PipelineJob> jobs = new ArrayList<>();
        addShape(jobs, nodes, "job-a", 4, "CLIENT_INTAKE", "CONTENT", "DESIGN", "SCHEDULING", "ADS");
        addShape(jobs, nodes, "job-b", 4, "CLIENT_INTAKE", "CONTENT", "DESIGN", "SCHEDULING", "PHOTO");
        addShape(jobs, nodes, "job-c", 4, "CLIENT_INTAKE", "CONTENT", "DESIGN", "SCHEDULING", "REPORTING");

        List<DiscoveredProcess> processes = discover(jobs, nodes);

        assertThat(processes).as("three shapes, three processes").hasSize(3);
        assertEveryProcessHoldsOneShape(processes);
    }

    @Test
    @DisplayName("every process the frozen corpus yields is internally one shape")
    void theFrozenCorpusYieldsOnlyPureProcesses() throws Exception {
        DiscoveryFixture fixture = DiscoveryFixture.load("multichat-extreme");

        List<StepKind> kinds =
                new StepDiscovery(WEIGHTS, fixture.directConversations()).discover(fixture.nodes(), fixture.excluded());
        List<DiscoveredProcess> processes =
                new ProcessDiscovery(WEIGHTS).discover(fixture.jobs(), fixture.nodes(), kinds);

        assertThat(processes)
                .as("the corpus discovers processes at all, or this proves nothing")
                .isNotEmpty();
        assertEveryProcessHoldsOneShape(processes);
    }

    private static void assertEveryProcessHoldsOneShape(List<DiscoveredProcess> processes) {
        for (DiscoveredProcess process : processes) {
            List<Set<String>> shapes = process.variants().stream()
                    .map(variant -> (Set<String>) new LinkedHashSet<>(variant))
                    .distinct()
                    .toList();

            assertThat(shapes)
                    .as(
                            "a process reported as one thing whose runs did different work is the worst "
                                    + "output this subsystem can produce: confidently wrong rather than silent. "
                                    + "Core %s, runs %d",
                            process.core(), process.runs())
                    .hasSize(1);
        }
    }

    private static List<DiscoveredProcess> discover(List<PipelineJob> jobs, List<PipelineNode> nodes) {
        List<StepKind> kinds = new StepDiscovery(WEIGHTS).discover(nodes, Set.of());
        return new ProcessDiscovery(WEIGHTS).discover(jobs, nodes, kinds);
    }

    private static void addShape(
            List<PipelineJob> jobs, List<PipelineNode> nodes, String prefix, int runs, String... types) {
        for (int engagement = 0; engagement < runs; engagement++) {
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
