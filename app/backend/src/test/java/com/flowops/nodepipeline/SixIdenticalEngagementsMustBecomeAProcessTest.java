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

class SixIdenticalEngagementsMustBecomeAProcessTest {
    private static final DiscoveryWeights WEIGHTS = DiscoveryWeights.reference();

    private static final List<String> SENTENCES = List.of(
            "wrote the summer menu copy and sent the draft over for review",
            "brief from the client about the autumn campaign and its budget",
            "built the paid social set and scheduled the flight for monday");

    @Test
    @DisplayName("six finished engagements with the same three steps become a process")
    void sixIdenticalEngagementsCluster() {
        List<PipelineNode> nodes = new ArrayList<>();
        List<PipelineJob> jobs = new ArrayList<>();

        for (int engagement = 0; engagement < 6; engagement++) {
            String jobId = "job-" + engagement;
            jobs.add(finishedJob(jobId));

            String[] types = {"CLIENT_INTAKE", "CONTENT", "ADS", "CLIENT_INTAKE", "CONTENT", "ADS"};
            for (int step = 0; step < types.length; step++) {
                nodes.add(node(jobId + "-n" + step, jobId, types[step], SENTENCES.get(step % SENTENCES.size())));
            }
        }

        List<StepKind> kinds = new StepDiscovery(WEIGHTS).discover(nodes, Set.of());

        assertThat(kinds)
                .as("three governed types, twelve nodes each across six engagements, all above the floor")
                .hasSize(3);

        List<DiscoveredProcess> processes = new ProcessDiscovery(WEIGHTS).discover(jobs, nodes, kinds);

        assertThat(processes)
                .as("six engagements share one step set exactly; the floor is three runs and three steps")
                .isNotEmpty();
        assertThat(processes.getFirst().runs())
                .as("every one of the six is a run of it — a smaller number means the cluster split")
                .isEqualTo(6);
    }

    @Test
    @DisplayName("seven engagements each holding one kind of work do not become a process")
    void oneStepRepeatedIsNotAProcess() {
        List<PipelineNode> nodes = new ArrayList<>();
        List<PipelineJob> jobs = new ArrayList<>();

        for (int engagement = 0; engagement < 7; engagement++) {
            String jobId = "one-off-" + engagement;
            jobs.add(finishedJob(jobId));
            for (int mark = 0; mark < 3; mark++) {
                nodes.add(node(jobId + "-n" + mark, jobId, "CONTENT", SENTENCES.get(mark % SENTENCES.size())));
            }
        }

        List<StepKind> kinds = new StepDiscovery(WEIGHTS).discover(nodes, Set.of());

        assertThat(kinds)
                .as("the kind of work is still found — that half was never wrong")
                .hasSize(1);

        assertThat(new ProcessDiscovery(WEIGHTS).discover(jobs, nodes, kinds))
                .as("seven runs of a one-step shape clears every floor and is still not a process")
                .isEmpty();
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
