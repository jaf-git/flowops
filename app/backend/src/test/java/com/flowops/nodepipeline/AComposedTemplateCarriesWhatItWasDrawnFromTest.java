package com.flowops.nodepipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.nodepipeline.domain.CandidateTemplate;
import com.flowops.nodepipeline.domain.PipelineNode;
import com.flowops.nodepipeline.domain.compose.Conversion;
import com.flowops.nodepipeline.domain.discovery.StepKind;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AComposedTemplateCarriesWhatItWasDrawnFromTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 2);

    @Test
    @DisplayName("it is named by the people who did the work, not by the commonest words in their messages")
    void namedByThePeople() {
        CandidateTemplate minted = Conversion.mint(deliveryKind(), delivered(), "T-1", TODAY, null);

        assertThat(minted.title()).isEqualTo("Store, schedule and post");
    }

    @Test
    @DisplayName("the commonest answer wins, not the first one seen")
    void theCommonestAnswerWins() {
        List<PipelineNode> nodes = List.of(
                marked("rain shell posted.", "Somebody's one-off name", "Scheduling and reporting"),
                marked("backpack posted.", "Store, schedule and post", "Scheduling and reporting"),
                marked("poles posted.", "Store, schedule and post", "Scheduling and reporting"));

        assertThat(Conversion.mint(deliveryKind(), nodes, "T-1", TODAY, null).title())
                .isEqualTo("Store, schedule and post");
    }

    @Test
    @DisplayName("it carries the checklist somebody wrote, and an empty one only when nobody wrote any")
    void carriesTheChecklist() {
        assertThat(Conversion.mint(deliveryKind(), delivered(), "T-1", TODAY, null)
                        .checklist())
                .containsExactly("Store the final files", "Schedule the post", "Confirm it went out");

        List<PipelineNode> unwritten = List.of(marked("rain shell posted.", null, "Scheduling and reporting"));
        assertThat(Conversion.mint(deliveryKind(), unwritten, "T-1", TODAY, null)
                        .checklist())
                .isEmpty();
    }

    @Test
    @DisplayName("its responsible role is a role name, because that is what the column means")
    void responsibleRoleIsARoleName() {
        CandidateTemplate minted = Conversion.mint(deliveryKind(), delivered(), "T-1", TODAY, null);

        assertThat(minted.responsibleRole()).isEqualTo("Scheduling and reporting");
        assertThat(minted.workType()).isEqualTo("DELIVERY");
    }

    @Test
    @DisplayName("carrying those fields is what lifts it over the matcher's substance floor")
    void clearsTheSubstanceFloor() {
        CandidateTemplate minted = Conversion.mint(deliveryKind(), delivered(), "T-1", TODAY, null);

        assertThat(minted.substance()).isGreaterThanOrEqualTo(2);
        assertThat(minted.eligibleFor(TODAY)).isFalse();
    }

    @Test
    @DisplayName("a kind nobody named still gets a name, from the words in the marks")
    void anUnnamedKindStillGetsAName() {
        List<PipelineNode> unnamed = List.of(
                marked("rain shell posted.", null, "Scheduling and reporting"),
                marked("backpack posted.", null, "Scheduling and reporting"));

        assertThat(Conversion.mint(deliveryKind(), unnamed, "T-1", TODAY, null).title())
                .isNotBlank();
    }

    @Test
    @DisplayName("the description carries what the cluster knows, and keeps the count as its last line")
    void describedFromTheEvidence() {
        CandidateTemplate minted = Conversion.mint(deliveryKind(), sequenced(), "T-1", TODAY, null);

        assertThat(minted.description())
                .contains("Scheduling and reporting does this work.")
                .contains("It normally follows Designer and is handed to Account manager.")
                .contains("Usually the 3rd step of its thread.")
                .contains("People doing it called it “Store, schedule and post”.")
                .endsWith("Drafted from 3 pieces of work across 2 engagements.");
    }

    @Test
    @DisplayName("a fact the cluster never agreed on gets no sentence")
    void silentWhereThereIsNoEvidence() {
        CandidateTemplate minted = Conversion.mint(deliveryKind(), delivered(), "T-1", TODAY, null);

        assertThat(minted.description())
                .doesNotContain("follows")
                .doesNotContain("handed to")
                .doesNotContain("step of its thread");
        assertThat(minted.precedingRole()).isNull();
        assertThat(minted.followingRole()).isNull();
        assertThat(minted.position()).isNull();
    }

    @Test
    @DisplayName("where the work sits in its thread is carried, not dropped")
    void theSequenceIsCarried() {
        CandidateTemplate minted = Conversion.mint(deliveryKind(), sequenced(), "T-1", TODAY, null);

        assertThat(minted.precedingRole()).isEqualTo("Designer");
        assertThat(minted.followingRole()).isEqualTo("Account manager");
        assertThat(minted.position()).isEqualTo(2);
        assertThat(minted.requiredInput()).isEqualTo("From Designer.");
        assertThat(minted.completionCriteria()).isEqualTo("Handed to Account manager.");
    }

    @Test
    @DisplayName("the expected output is left empty rather than guessed from the work type")
    void theOutputIsNotInvented() {
        CandidateTemplate minted = Conversion.mint(deliveryKind(), sequenced(), "T-1", TODAY, null);

        assertThat(minted.expectedOutput()).isNull();
    }

    @Test
    @DisplayName("the checklist pools every step anybody wrote, commonest first")
    void theChecklistIsPooled() {
        List<PipelineNode> disagreeing = List.of(
                withSteps(List.of("Store the final files", "Schedule the post")),
                withSteps(List.of("Store the final files", "Confirm it went out")),
                withSteps(List.of("store the final files ", "Schedule the post")));

        CandidateTemplate minted = Conversion.mint(deliveryKind(), disagreeing, "T-1", TODAY, null);

        assertThat(minted.checklist())
                .containsExactly("Store the final files", "Schedule the post", "Confirm it went out");
    }

    private static List<PipelineNode> sequenced() {
        return delivered().stream()
                .map(node -> placed(node, "Designer", "Account manager", 2))
                .toList();
    }

    private static PipelineNode placed(PipelineNode node, String before, String after, int position) {
        return new PipelineNode(
                node.id(),
                node.jobId(),
                node.text(),
                node.detail(),
                node.creatorId(),
                node.markerId(),
                node.kind(),
                node.creatorRole(),
                node.performerRole(),
                node.createdAt(),
                node.closure(),
                node.direction(),
                node.postClose(),
                node.outputType(),
                node.workType(),
                node.taskTemplateId(),
                node.disrupted(),
                node.conversationId(),
                before,
                null,
                after,
                position,
                node.title(),
                node.checklist());
    }

    private static PipelineNode withSteps(List<String> steps) {
        PipelineNode base = marked("posted.", "Store, schedule and post", "Scheduling and reporting");
        return new PipelineNode(
                base.id(),
                base.jobId(),
                base.text(),
                base.detail(),
                base.creatorId(),
                base.markerId(),
                base.kind(),
                base.creatorRole(),
                base.performerRole(),
                base.createdAt(),
                base.closure(),
                base.direction(),
                base.postClose(),
                base.outputType(),
                base.workType(),
                base.taskTemplateId(),
                base.disrupted(),
                base.conversationId(),
                base.precedingRole(),
                base.precedingDirection(),
                base.followingRole(),
                base.positionInTrack(),
                base.title(),
                steps);
    }

    private static StepKind deliveryKind() {
        return new StepKind(
                "K:DELIVERY",
                "DELIVERY",
                null,
                null,
                List.of("n1", "n2", "n3"),
                java.util.Set.of("job-1", "job-2"),
                List.of("posted", "scheduled"),
                0.6,
                0.7);
    }

    private static List<PipelineNode> delivered() {
        return List.of(
                marked("rain shell posted.", "Store, schedule and post", "Scheduling and reporting"),
                marked("backpack posted.", "Store, schedule and post", "Scheduling and reporting"),
                marked("poles posted.", "Store, schedule and post", "Scheduling and reporting"));
    }

    private static PipelineNode marked(String text, String title, String performerRole) {
        return new PipelineNode(
                UUID.randomUUID().toString(),
                "job-1",
                text,
                null,
                UUID.nameUUIDFromBytes("mihai".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                null,
                "WORK",
                null,
                performerRole,
                TODAY,
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
                title == null ? null : List.of("Store the final files", "Schedule the post", "Confirm it went out"));
    }
}
