package com.flowops.nodepipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowops.nodepipeline.domain.discovery.DiscoveredProcess;
import com.flowops.nodepipeline.domain.discovery.DiscoveryWeights;
import com.flowops.nodepipeline.domain.discovery.ProcessDiscovery;
import com.flowops.nodepipeline.domain.discovery.StepDiscovery;
import com.flowops.nodepipeline.domain.discovery.StepKind;
import java.util.List;
import org.junit.jupiter.api.Test;

class ColdStartDiscoveryTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void theExtremeCorpusYields12KindsOfWorkAnd3Processes() throws Exception {
        DiscoveryFixture fixture = DiscoveryFixture.load("multichat-extreme");

        List<StepKind> kinds = new StepDiscovery(DiscoveryWeights.reference(), fixture.directConversations())
                .discover(fixture.nodes(), fixture.excluded());

        assertThat(kinds).as("ADR-017 takes this from 9 to 12").hasSize(12);

        List<DiscoveredProcess> processes =
                new ProcessDiscovery(DiscoveryWeights.reference()).discover(fixture.jobs(), fixture.nodes(), kinds);

        assertThat(processes)
                .as("and processes discovered from 2 to 3 — the same change, measured at the other end")
                .hasSize(3);
    }

    @Test
    void theThreeDirectMessageSubprocessesAppearAsTheirOwnKinds() throws Exception {
        DiscoveryFixture fixture = DiscoveryFixture.load("multichat-extreme");

        List<StepKind> kinds = new StepDiscovery(DiscoveryWeights.reference(), fixture.directConversations())
                .discover(fixture.nodes(), fixture.excluded());

        assertThat(kinds.stream().filter(StepKind::isSubprocess).map(StepKind::id))
                .containsExactlyInAnyOrder("K:WRITER/dm-sk", "K:PHOTO/dm-tk", "K:DESIGN/dm-ka");
    }

    @Test
    void theSplitFollowsTheGraphRatherThanTheSpellingOfAnIdentifier() throws Exception {
        DiscoveryFixture fixture = DiscoveryFixture.load("multichat-extreme");

        List<StepKind> told = new StepDiscovery(DiscoveryWeights.reference(), fixture.directConversations())
                .discover(fixture.nodes(), fixture.excluded());
        List<StepKind> notTold =
                new StepDiscovery(DiscoveryWeights.reference()).discover(fixture.nodes(), fixture.excluded());

        assertThat(told).as("told which streams are direct").hasSize(12);
        assertThat(notTold)
                .as("told nothing — which is what every real workspace got, because no id began with dm-")
                .hasSize(9);
        assertThat(notTold.stream().filter(StepKind::isSubprocess).toList())
                .as("and not one subprocess was ever found")
                .isEmpty();
    }

    @Test
    void noTwoKindsShareAnIdentity() throws Exception {
        for (String corpus : List.of("multichat-extreme", "multichat-medium")) {
            DiscoveryFixture fixture = DiscoveryFixture.load(corpus);
            List<StepKind> kinds = new StepDiscovery(DiscoveryWeights.reference(), fixture.directConversations())
                    .discover(fixture.nodes(), fixture.excluded());

            assertThat(kinds.stream().map(StepKind::id).toList())
                    .as("%s", corpus)
                    .doesNotHaveDuplicates();
        }
    }

    @Test
    void everyKindHoldsExactlyTheNodesTheOracleGaveIt() throws Exception {
        for (String corpus : List.of("multichat-extreme", "multichat-medium")) {
            DiscoveryFixture fixture = DiscoveryFixture.load(corpus);
            List<StepKind> kinds = new StepDiscovery(DiscoveryWeights.reference(), fixture.directConversations())
                    .discover(fixture.nodes(), fixture.excluded());

            assertThat(kinds.stream().map(StepKind::id).sorted().toList())
                    .as("%s: the same kinds", corpus)
                    .isEqualTo(fixture.expectedKindIds().stream().sorted().toList());

            for (StepKind kind : kinds) {
                assertThat(kind.nodeIds())
                        .as("%s: %s holds the same nodes", corpus, kind.id())
                        .isEqualTo(fixture.expectedNodesOfKind().get(kind.id()));
            }
        }
    }

    @Test
    void anOrderIsNotShownUntilItHasEarnedIt() throws Exception {
        DiscoveryFixture fixture = DiscoveryFixture.load("multichat-extreme");
        List<StepKind> kinds = new StepDiscovery(DiscoveryWeights.reference(), fixture.directConversations())
                .discover(fixture.nodes(), fixture.excluded());
        List<DiscoveredProcess> processes =
                new ProcessDiscovery(DiscoveryWeights.reference()).discover(fixture.jobs(), fixture.nodes(), kinds);

        for (DiscoveredProcess process : processes) {
            if (process.runs() < 6) {
                assertThat(process.orderReliable())
                        .as("%d runs is not enough to claim an order", process.runs())
                        .isFalse();
                assertThat(process.displayOrder())
                        .as("so what is shown is visibly not a sequence")
                        .isEqualTo(process.core().stream().sorted().toList());
            }
            assertThat(process.core().size())
                    .as("a two-step process is a pair of tasks, not a way of working")
                    .isGreaterThanOrEqualTo(3);
            assertThat(process.runs()).as("two runs is a coincidence").isGreaterThanOrEqualTo(3);
        }
    }

    @Test
    void aWithheldOrderSaysWhichOfTheTwoConditionsFailed() throws Exception {
        DiscoveryFixture fixture = DiscoveryFixture.load("multichat-extreme");
        DiscoveryWeights weights = DiscoveryWeights.reference();
        List<StepKind> kinds =
                new StepDiscovery(weights, fixture.directConversations()).discover(fixture.nodes(), fixture.excluded());
        List<DiscoveredProcess> processes =
                new ProcessDiscovery(weights).discover(fixture.jobs(), fixture.nodes(), kinds);

        assertThat(processes).as("the corpus must produce something to check").isNotEmpty();

        for (DiscoveredProcess process : processes) {
            if (process.orderReliable()) {
                assertThat(process.orderWithheldReason())
                        .as("a shown order withholds nothing, so it has nothing to explain")
                        .isNull();
                continue;
            }

            assertThat(process.orderWithheldReason())
                    .as("an order that is not shown must say why")
                    .isNotNull();

            boolean tooFewRuns = process.runs() < weights.orderMinimumRuns();
            boolean tooLittleAgreement = process.orderConfidence() < weights.orderConfidenceFloor();

            assertThat(process.orderWithheldReason().contains("runs"))
                    .as(
                            "%d runs against a floor of %d: the run count is %sthe reason, and the sentence must agree",
                            process.runs(), weights.orderMinimumRuns(), tooFewRuns ? "" : "not ")
                    .isEqualTo(tooFewRuns);
            assertThat(process.orderWithheldReason().contains("agree"))
                    .as(
                            "order agreement %.3f against a floor of %.2f",
                            process.orderConfidence(), weights.orderConfidenceFloor())
                    .isEqualTo(tooLittleAgreement);

            assertThat(journalledReason(process).length())
                    .as(
                            "pipeline_decision.reason is varchar(80); a longer sentence aborts the whole"
                                    + " run with a 500, and the sentence was: %s",
                            journalledReason(process))
                    .isLessThanOrEqualTo(REASON_COLUMN);
        }
    }

    private static final int REASON_COLUMN = 80;

    private static String journalledReason(DiscoveredProcess process) {
        return "%d runs, %s (%.3f)"
                .formatted(
                        process.runs(),
                        process.orderReliable() ? "order reliable" : process.orderWithheldReason(),
                        process.orderConfidence());
    }
}
