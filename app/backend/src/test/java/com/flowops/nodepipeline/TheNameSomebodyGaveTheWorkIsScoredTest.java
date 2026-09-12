package com.flowops.nodepipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.nodepipeline.domain.CandidateTemplate;
import com.flowops.nodepipeline.domain.PipelineNode;
import com.flowops.nodepipeline.domain.match.Affinity;
import com.flowops.nodepipeline.domain.match.MatchWeights;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TheNameSomebodyGaveTheWorkIsScoredTest {
    private static final MatchWeights WEIGHTS = MatchWeights.reference();

    private static final CandidateTemplate STORE_SCHEDULE_AND_POST = new CandidateTemplate(
            "T-DELIVERY",
            "Store, schedule and post",
            "Drafted from 16 pieces of work across 4 jobs.",
            "DELIVERY",
            "Scheduling and reporting",
            null,
            null,
            null,
            null,
            List.of(),
            "APPROVED",
            LocalDate.of(2026, 6, 1),
            List.of(),
            null,
            null,
            null);

    @Test
    @DisplayName("the message alone barely agrees with a well-named template")
    void theMessageAloneBarelyAgrees() {
        double said = Affinity.text(marked("rain shell posted.", null), STORE_SCHEDULE_AND_POST, WEIGHTS, null);

        assertThat(said).isLessThan(WEIGHTS.textVeto());
    }

    @Test
    @DisplayName("the same mark, once somebody has named the work, clears the veto")
    void namingTheWorkClearsTheVeto() {
        PipelineNode named = marked("rain shell posted.", "Store, schedule and post");

        double scored = Affinity.text(named, STORE_SCHEDULE_AND_POST, WEIGHTS, null);

        assertThat(scored).isGreaterThanOrEqualTo(WEIGHTS.textVeto());
    }

    @Test
    @DisplayName("a node nobody named scores exactly what it scored before — the change only ever raises")
    void anUnnamedNodeIsUnaffected() {
        PipelineNode unnamed = marked("rain shell posted.", null);
        PipelineNode blank = marked("rain shell posted.", "   ");

        double messageOnly = Affinity.text(unnamed, STORE_SCHEDULE_AND_POST, WEIGHTS, null);

        assertThat(Affinity.text(blank, STORE_SCHEDULE_AND_POST, WEIGHTS, null)).isEqualTo(messageOnly);
    }

    @Test
    @DisplayName("a title describing different work cannot carry a mark onto this template")
    void anUnrelatedTitleCannotCarryTheMark() {
        PipelineNode misnamed = marked("rain shell posted.", "Concept and brand check");

        double misnamedScore = Affinity.text(misnamed, STORE_SCHEDULE_AND_POST, WEIGHTS, null);

        assertThat(misnamedScore).isLessThan(WEIGHTS.textVeto());
    }

    private static PipelineNode marked(String text, String title) {
        return new PipelineNode(
                UUID.randomUUID().toString(),
                "job-1",
                text,
                null,
                UUID.nameUUIDFromBytes("mihai".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                null,
                "WORK",
                null,
                "Scheduling and reporting",
                LocalDate.of(2026, 6, 10),
                PipelineNode.Closure.MARKED,
                "STANDALONE",
                false,
                null,
                "DELIVERY",
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                title,
                null);
    }
}
