package com.flowops.discovery.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.discovery.domain.enums.Direction;
import com.flowops.discovery.domain.enums.NodeKind;
import com.flowops.discovery.domain.exception.NotYoursToDescribeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("DISCOVERY-ENRICH-NODE-01")
class EnrichmentFillsAnEmptyFieldAndNeverChangesASetOneTest {
    private static final UUID MARIA = UUID.randomUUID();
    private static final UUID ANDREI = UUID.randomUUID();

    private WorkNode aNode() {
        return WorkNode.marked(
                WorkNodeId.of(UUID.randomUUID()),
                JobId.of(UUID.randomUUID()),
                "Fine by me, that gives us room.",
                MARIA,
                null,
                NodeKind.WORK,
                Direction.REQUEST,
                Instant.parse("2026-08-31T09:00:00Z"));
    }

    @Test
    void aFreshNodeHasNoTitleNoDetailAndNoChecklist() {
        WorkNode node = aNode();

        assertThat(node.title()).isEmpty();
        assertThat(node.detail()).isEmpty();
        assertThat(node.checklist()).isEmpty();
    }

    @Test
    void thefirstAnswerIsTaken() {
        WorkNode node = aNode();

        assertThat(node.enrichedWith(
                        MARIA, "caption set for Aurora", "three posts and a story", List.of("draft", "review")))
                .isTrue();

        assertThat(node.title()).contains("caption set for Aurora");
        assertThat(node.detail()).contains("three posts and a story");
        assertThat(node.checklist()).contains(List.of("draft", "review"));
    }

    @Test
    void theAuthorMayCorrectTheirOwnDescriptionWhileTheNodeIsOpen() {
        WorkNode node = aNode();
        node.enrichedWith(MARIA, "caption ste for Aurora", null, null);

        assertThat(node.enrichedWith(MARIA, "caption set for Aurora", null, null))
                .isTrue();
        assertThat(node.title()).contains("caption set for Aurora");
    }

    @Test
    void somebodyElseMayNotRewriteAnAnswerThatIsAlreadyThere() {
        WorkNode node = aNode();
        node.requestedOf(ANDREI, null);
        node.enrichedWith(MARIA, "caption set for Aurora", null, null);

        assertThatThrownBy(() -> node.enrichedWith(ANDREI, "something else entirely", null, null))
                .isInstanceOf(NotYoursToDescribeException.class);
        assertThat(node.title()).contains("caption set for Aurora");
    }

    @Test
    void thePerformerMayFillAnEmptyFieldWithoutBecomingTheAuthor() {
        WorkNode node = aNode();
        node.requestedOf(ANDREI, null);
        node.enrichedWith(MARIA, "caption set for Aurora", null, null);

        assertThat(node.enrichedWith(ANDREI, null, "three posts and a story", null))
                .isTrue();
        assertThat(node.detail()).contains("three posts and a story");
        assertThat(node.enrichedBy()).contains(MARIA);
    }

    @Test
    void abystanderMayNotDescribeSomebodyElsesWork() {
        WorkNode node = aNode();
        node.requestedOf(ANDREI, null);

        assertThatThrownBy(() -> node.enrichedWith(UUID.randomUUID(), "not mine to name", null, null))
                .isInstanceOf(NotYoursToDescribeException.class);
        assertThat(node.title()).isEmpty();
    }

    @Test
    void aSettledNodesAnswerCannotBeRewordedButItsGapsCanStillBeFilled() {
        WorkNode node = aNode();
        node.requestedOf(ANDREI, null);
        node.enrichedWith(MARIA, "caption set for Aurora", null, null);
        node.started(Instant.parse("2026-08-31T10:00:00Z"));
        node.completed(com.flowops.discovery.domain.enums.OutputType.TEXT);
        node.closed(Instant.parse("2026-08-31T11:00:00Z"));

        assertThatThrownBy(() -> node.enrichedWith(MARIA, "a better name", null, null))
                .isInstanceOf(NotYoursToDescribeException.class);

        assertThat(node.enrichedWith(MARIA, null, "three posts and a story", null))
                .isTrue();
    }

    @Test
    void anUnansweredFieldDoesNotEraseAnAnsweredOne() {
        WorkNode node = aNode();
        node.enrichedWith(MARIA, "caption set for Aurora", "three posts and a story", null);

        assertThat(node.enrichedWith(MARIA, null, null, List.of("draft"))).isTrue();

        assertThat(node.title()).contains("caption set for Aurora");
        assertThat(node.detail()).contains("three posts and a story");
        assertThat(node.checklist()).contains(List.of("draft"));
    }

    @Test
    void aBlankTitleIsTreatedAsUnanswered() {
        WorkNode node = aNode();

        assertThat(node.enrichedWith(MARIA, "   ", null, null)).isFalse();
        assertThat(node.title()).isEmpty();
    }

    @Test
    void anEmptyChecklistIsAnAnswerAndSilenceIsNot() {
        WorkNode saidThereAreNone = aNode();
        assertThat(saidThereAreNone.enrichedWith(MARIA, null, null, List.of())).isTrue();
        assertThat(saidThereAreNone.checklist()).contains(List.of());

        WorkNode neverAsked = aNode();
        assertThat(neverAsked.enrichedWith(MARIA, null, null, null)).isFalse();
        assertThat(neverAsked.checklist()).isEmpty();
    }

    @Test
    void theChecklistIsCopiedRatherThanShared() {
        WorkNode node = aNode();
        List<String> mine = new ArrayList<>(List.of("draft"));

        node.enrichedWith(MARIA, null, null, mine);
        mine.add("smuggled in afterwards");

        assertThat(node.checklist()).contains(List.of("draft"));
        assertThatThrownBy(() -> node.checklist().orElseThrow().add("or through the getter"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void describingWorkIsNotMovingIt() {
        WorkNode node = aNode();
        int before = node.pendingTransitions().size();

        node.enrichedWith(MARIA, "caption set for Aurora", null, null);

        assertThat(node.pendingTransitions()).hasSize(before);
    }
}
