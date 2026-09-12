package com.flowops.aiinsight.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.aiinsight.domain.BlockPatternDetection.BlockOccurrence;
import com.flowops.aiinsight.domain.BlockPatternDetection.BlockPattern;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("AI-INSIGHT-BLOCK-PATTERN-01")
class BlockPatternDetectionTest {
    private static BlockOccurrence blocked(String step, String reason) {
        return new BlockOccurrence(UUID.randomUUID(), step, reason);
    }

    @Test
    void findsAStepThatKeepsStoppingForTheSameReason() {
        List<BlockOccurrence> blocks = new ArrayList<>();
        for (int run = 0; run < 5; run++) {
            blocks.add(blocked("Proposal", "waiting on finance for the cost basis"));
        }

        List<BlockPattern> found = BlockPatternDetection.over(blocks);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).stepTitle()).isEqualTo("Proposal");
        assertThat(found.get(0).occurrences()).isEqualTo(5);
        assertThat(found.get(0).reason())
                .as("reported in the words somebody actually typed — a person recognising their own"
                        + " sentence is what makes the finding credible")
                .isEqualTo("waiting on finance for the cost basis");
    }

    @Test
    void saysNothingBelowThreeOccurrences() {
        List<BlockOccurrence> twice =
                List.of(blocked("Proposal", "waiting on finance"), blocked("Proposal", "waiting on finance"));

        assertThat(BlockPatternDetection.over(twice)).isEmpty();
    }

    @Test
    void groupsReasonsDifferingOnlyInCaseOrPunctuation() {
        List<BlockOccurrence> blocks = List.of(
                blocked("Proposal", "Waiting on finance."),
                blocked("Proposal", "waiting on finance"),
                blocked("Proposal", "WAITING ON  FINANCE!"));

        assertThat(BlockPatternDetection.over(blocks)).hasSize(1);
    }

    @Test
    void doesNotGroupReasonsThatOnlyAPersonWouldCallTheSame() {
        List<BlockOccurrence> blocks = List.of(
                blocked("Proposal", "waiting on finance"),
                blocked("Proposal", "finance have not come back"),
                blocked("Proposal", "no cost basis yet"));

        assertThat(BlockPatternDetection.over(blocks)).isEmpty();
    }

    @Test
    void keepsStepsApartEvenWhenTheReasonMatches() {
        List<BlockOccurrence> blocks = new ArrayList<>();
        for (int run = 0; run < 3; run++) {
            blocks.add(blocked("Proposal", "waiting on finance"));
            blocks.add(blocked("Contract", "waiting on finance"));
        }

        List<BlockPattern> found = BlockPatternDetection.over(blocks);

        assertThat(found).hasSize(2);
        assertThat(found).extracting(BlockPattern::stepTitle).containsExactlyInAnyOrder("Proposal", "Contract");
    }

    @Test
    void ordersSeveralPatternsByHowOftenEachHappens() {
        List<BlockOccurrence> blocks = new ArrayList<>();
        for (int run = 0; run < 3; run++) {
            blocks.add(blocked("Contract", "client unresponsive"));
        }
        for (int run = 0; run < 6; run++) {
            blocks.add(blocked("Proposal", "waiting on finance"));
        }

        List<BlockPattern> found = BlockPatternDetection.over(blocks);

        assertThat(found.get(0).stepTitle()).isEqualTo("Proposal");
        assertThat(found.get(0).occurrences()).isEqualTo(6);
    }

    @Test
    void ignoresABlockWithNoReasonRatherThanClusteringOnEmptiness() {
        List<BlockOccurrence> blocks =
                List.of(blocked("Proposal", ""), blocked("Proposal", "   "), blocked("Proposal", null));

        assertThat(BlockPatternDetection.over(blocks)).isEmpty();
    }

    @Test
    void carriesTheRunsItCountedSoTheClaimCanBeChecked() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        List<BlockOccurrence> blocks = List.of(
                new BlockOccurrence(first, "Proposal", "waiting on finance"),
                new BlockOccurrence(second, "Proposal", "waiting on finance"),
                new BlockOccurrence(first, "Proposal", "waiting on finance"));

        assertThat(BlockPatternDetection.over(blocks).get(0).instances()).containsExactlyInAnyOrder(first, second);
    }
}
