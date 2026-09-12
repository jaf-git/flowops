package com.flowops.nodepipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.nodepipeline.domain.PipelineNode;
import com.flowops.nodepipeline.domain.ai.ConceptSplit;
import com.flowops.nodepipeline.domain.ai.Judgement;
import com.flowops.nodepipeline.domain.discovery.DiscoveredProcess;
import com.flowops.nodepipeline.domain.discovery.DiscoveryWeights;
import com.flowops.nodepipeline.domain.discovery.ProcessDiscovery;
import com.flowops.nodepipeline.domain.discovery.StepDiscovery;
import com.flowops.nodepipeline.domain.discovery.StepKind;
import com.flowops.nodepipeline.infrastructure.model.OllamaWorkJudgeAdapter;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("NODEPIPE-AI-01")
@Tag("livemodel")
class TheModelDoesNotDestroyProcessDiscoveryTest {
    private static final String CORPUS = "multichat-extreme";
    private static final int PROCESSES_EXPECTED = 3;
    private static final int BUDGET = 400;

    @Test
    void processDiscoveryIsUnreachableByTheModelAndStillFindsThree() throws Exception {
        DiscoveryFixture corpus = DiscoveryFixture.load(CORPUS);
        DiscoveryWeights weights = DiscoveryWeights.reference();

        List<StepKind> coarse =
                new StepDiscovery(weights, corpus.directConversations()).discover(corpus.nodes(), corpus.excluded());
        List<DiscoveredProcess> processes =
                new ProcessDiscovery(weights).discover(corpus.jobs(), corpus.nodes(), coarse);

        assertThat(processes)
                .as("ADR-009: the unit process discovery clusters on is structural, and no model may touch it")
                .hasSize(PROCESSES_EXPECTED);
    }

    @Test
    void refiningForDraftingMakesEveryKindItSplitsMoreCoherent() throws Exception {
        DiscoveryFixture corpus = DiscoveryFixture.load(CORPUS);
        DiscoveryWeights weights = DiscoveryWeights.reference();
        StepDiscovery discovery = new StepDiscovery(weights, corpus.directConversations());

        OllamaWorkJudgeAdapter model = new OllamaWorkJudgeAdapter(
                "http://localhost:11434",
                "llama3.2:3b",
                30,
                BUDGET,
                false,
                true,
                false,
                false,
                new com.flowops.nodepipeline.application.AiSwitch());

        List<StepKind> coarse = discovery.discover(corpus.nodes(), corpus.excluded());
        List<StepKind> refined =
                discovery.refinedForDrafting(corpus.nodes(), corpus.excluded(), node -> conceptOf(model, node));

        assertThat(model.callsRemaining())
                .as("the model must actually have been asked, or nothing below means anything")
                .isLessThan(BUDGET);

        assertThat(refined.size())
                .as("splitting only ever divides, so there cannot be fewer kinds than before")
                .isGreaterThanOrEqualTo(coarse.size());

        for (StepKind part : refined) {
            String parent =
                    part.id().contains("#") ? part.id().substring(0, part.id().indexOf('#')) : null;
            if (parent == null) {
                continue;
            }
            double before = coarse.stream()
                    .filter(kind -> kind.id().equals(parent))
                    .mapToDouble(StepKind::cohesion)
                    .findFirst()
                    .orElseThrow();

            assertThat(part.cohesion())
                    .as(
                            "%s came out of %s (cohesion %.2f) — a split that lowers cohesion is fragmentation, "
                                    + "which is the 3 -> 0 failure. Read ADR-010 and ADR-011 against ConceptSplit",
                            part.id(), parent, before)
                    .isGreaterThanOrEqualTo(before);
        }
    }

    private static String conceptOf(OllamaWorkJudgeAdapter model, PipelineNode node) {
        Set<String> allowed = ConceptSplit.conceptsFor(node.workType());
        if (allowed.isEmpty()) {
            return null;
        }
        return model.judge(new Judgement.Question(
                        Judgement.PlugPoint.SAME_KIND, node.text(), List.of(), String.join(", ", allowed)))
                .map(Judgement.Verdict::value)
                .filter(allowed::contains)
                .orElse(null);
    }
}
