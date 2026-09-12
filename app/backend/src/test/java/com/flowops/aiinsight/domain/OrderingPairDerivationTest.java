package com.flowops.aiinsight.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.aiinsight.domain.OrderingPairDerivation.OrderingPair;
import com.flowops.aiinsight.domain.OrderingPairDerivation.TaskObservation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderingPairDerivationTest {
    private static final Instant NOON = Instant.parse("2026-08-24T12:00:00Z");

    @Test
    @DisplayName("two tasks in different runs, adjacent in time, produce no ordering")
    void adjacencyAcrossRunsIsNotAnOrdering() {
        UUID onboarding = UUID.randomUUID();
        UUID invoiceChase = UUID.randomUUID();
        List<TaskObservation> parallelWork = List.of(
                new TaskObservation(UUID.randomUUID(), onboarding, "send welcome pack", NOON),
                new TaskObservation(UUID.randomUUID(), invoiceChase, "chase unpaid invoice", NOON.plusSeconds(660)));

        assertThat(OrderingPairDerivation.over(parallelWork))
                .as("eleven minutes apart and in different runs is a fact about the clock, not about the work")
                .isEmpty();
    }

    @Test
    @DisplayName("the same two timestamps inside one run do produce an ordering")
    void adjacencyWithinOneRunIsAnOrdering() {
        UUID onboarding = UUID.randomUUID();
        List<TaskObservation> oneRun = List.of(
                new TaskObservation(UUID.randomUUID(), onboarding, "send welcome pack", NOON),
                new TaskObservation(UUID.randomUUID(), onboarding, "chase unpaid invoice", NOON.plusSeconds(660)));

        assertThat(OrderingPairDerivation.over(oneRun))
                .as("the boundary is the only difference from the case above; if this is empty the test above proves"
                        + " nothing")
                .containsExactly(new OrderingPair("send welcome pack", "chase unpaid invoice", 1));
    }

    @Test
    @DisplayName("ad-hoc tasks cluster and recur, and never order")
    void adHocWorkCountsEverywhereExceptInOrder() {
        List<TaskObservation> adHoc = List.of(
                new TaskObservation(UUID.randomUUID(), null, "prepare quote", NOON),
                new TaskObservation(UUID.randomUUID(), null, "send quote", NOON.plusSeconds(60)),
                new TaskObservation(UUID.randomUUID(), null, "prepare quote", NOON.plusSeconds(4000)));

        assertThat(OrderingPairDerivation.over(adHoc))
                .as("no run means no case, and a sequence read across cases is an artefact of the clock")
                .isEmpty();
        assertThat(OrderingPairDerivation.recurrenceOf(adHoc))
                .as("recurrence needs no case to be true, so ad-hoc work is fully counted here")
                .containsEntry("prepare quote", 2)
                .containsEntry("send quote", 1);
        assertThat(OrderingPairDerivation.shareARun(adHoc))
                .as("the surface renders the set and no graph, and this is how it knows to")
                .isFalse();
    }

    @Test
    @DisplayName("a parallelism that repeats accumulates no evidence at all")
    void aRepeatedPhantomNeverBecomesConfident() {
        List<TaskObservation> tenWeeksOfParallelWork = java.util.stream.IntStream.range(0, 10)
                .boxed()
                .flatMap(week -> java.util.stream.Stream.of(
                        new TaskObservation(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                "send welcome pack",
                                NOON.plusSeconds(week * 604_800L)),
                        new TaskObservation(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                "chase unpaid invoice",
                                NOON.plusSeconds(week * 604_800L + 660))))
                .toList();

        assertThat(OrderingPairDerivation.over(tenWeeksOfParallelWork))
                .as("a threshold would have risen with the phantom over ten weeks; a wall does not move")
                .isEmpty();
    }

    @Test
    @DisplayName("one run holding the same ordering twice is still one run's worth of evidence")
    void repetitionWithinARunIsCountedOnce() {
        UUID run = UUID.randomUUID();
        List<TaskObservation> repeated = List.of(
                new TaskObservation(UUID.randomUUID(), run, "draft", NOON),
                new TaskObservation(UUID.randomUUID(), run, "review", NOON.plusSeconds(100)),
                new TaskObservation(UUID.randomUUID(), run, "draft", NOON.plusSeconds(200)),
                new TaskObservation(UUID.randomUUID(), run, "review", NOON.plusSeconds(300)));

        assertThat(OrderingPairDerivation.over(repeated))
                .extracting(OrderingPair::runsObserved)
                .as("counting occurrences rather than runs would let one run look like several")
                .containsOnly(1);
    }
}
