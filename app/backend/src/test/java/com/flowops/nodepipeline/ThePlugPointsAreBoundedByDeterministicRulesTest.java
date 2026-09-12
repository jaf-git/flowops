package com.flowops.nodepipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.nodepipeline.domain.MatchTier;
import com.flowops.nodepipeline.domain.ai.ConceptSplit;
import com.flowops.nodepipeline.domain.ai.Judgement;
import com.flowops.nodepipeline.domain.ai.LabelGrounding;
import com.flowops.nodepipeline.domain.ai.SameWorkGate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ThePlugPointsAreBoundedByDeterministicRulesTest {
    @Test
    void theModelIsAskedOnlyWhereTheDeterministicAnswerIsGenuinelyUncertain() {
        assertThat(SameWorkGate.inUncertaintyBand(0.60, 0.55, 0.45, 0.70))
                .as("the words agree enough to be worth asking about, and the score fell short")
                .isTrue();
        assertThat(SameWorkGate.inUncertaintyBand(0.30, 0.55, 0.45, 0.70))
                .as("below the text veto there is nothing to rescue — ADR-008 is absolute")
                .isFalse();
        assertThat(SameWorkGate.inUncertaintyBand(0.90, 0.85, 0.45, 0.70))
                .as("above the floor the deterministic answer already stands; asking spends a call to "
                        + "be told what is known")
                .isFalse();
    }

    @Test
    void aModelSayingDifferentVetoesTheMatch() {
        assertThat(SameWorkGate.decide(MatchTier.NUDGE, verdict("DIFFERENT", 0.9), false, 0.6))
                .isEqualTo(MatchTier.ABSTAIN);
        assertThat(SameWorkGate.decide(MatchTier.ROLE_MISMATCH, verdict("DIFFERENT", 0.7), false, 0.6))
                .isEqualTo(MatchTier.ABSTAIN);
    }

    @Test
    void aModelSayingUnsureCanNeverProduceAMessage() {
        assertThat(SameWorkGate.decide(MatchTier.NUDGE, verdict("UNSURE", 0.5), false, 0.6))
                .isEqualTo(MatchTier.ABSTAIN);
        assertThat(SameWorkGate.decide(MatchTier.OK, verdict("UNSURE", 0.5), false, 0.6))
                .as("but it does not disturb a tier that was not going to send a message anyway")
                .isEqualTo(MatchTier.OK);
    }

    @Test
    void aModelSayingSameMayOnlyRescueAnAbstentionThatFailedOnScoreAlone() {
        assertThat(SameWorkGate.decide(MatchTier.ABSTAIN, verdict("SAME", 0.9), true, 0.6))
                .as("below_floor, and the model is confident")
                .isEqualTo(MatchTier.NUDGE);

        assertThat(SameWorkGate.decide(MatchTier.ABSTAIN, verdict("SAME", 0.9), false, 0.6))
                .as("a text veto or a gate is not something a model may overturn")
                .isEqualTo(MatchTier.ABSTAIN);

        assertThat(SameWorkGate.decide(MatchTier.ABSTAIN, verdict("SAME", 0.4), true, 0.6))
                .as("and an unconfident SAME rescues nothing")
                .isEqualTo(MatchTier.ABSTAIN);
    }

    @Test
    void noOpinionAndAnUnrecognisedOpinionBothChangeNothing() {
        assertThat(SameWorkGate.decide(MatchTier.NUDGE, null, false, 0.6)).isEqualTo(MatchTier.NUDGE);
        assertThat(SameWorkGate.decide(MatchTier.NUDGE, verdict("PROBABLY", 0.9), false, 0.6))
                .isEqualTo(MatchTier.NUDGE);
    }

    @Test
    void oneConceptPlusEverythingElseDoesNotSplitABucket() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("n1", "CAPTIONS");
        labels.put("n2", "CAPTIONS");
        labels.put("n3", "CAPTIONS");
        labels.put("n4", null);
        labels.put("n5", null);
        labels.put("n6", null);

        Map<String, List<String>> groups = ConceptSplit.split(labels, "CONTENT", 3);

        assertThat(groups)
                .as("one concept is not two competing concepts, so there is nothing to split on")
                .hasSize(1);
        assertThat(groups.values().iterator().next()).hasSize(6);
    }

    @Test
    void twoCompetingConceptsSplitAndTheModelsSilenceJoinsTheLargest() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("n1", "CAPTIONS");
        labels.put("n2", "CAPTIONS");
        labels.put("n3", "CAPTIONS");
        labels.put("n4", "CAPTIONS");
        labels.put("n5", "BLOG");
        labels.put("n6", "BLOG");
        labels.put("n7", "BLOG");
        labels.put("n8", null);
        labels.put("n9", null);

        Map<String, List<String>> groups = ConceptSplit.split(labels, "CONTENT", 3);

        assertThat(groups).containsOnlyKeys("CAPTIONS", "BLOG");
        assertThat(groups.get("CAPTIONS"))
                .as("the unlabelled join the largest real group — they are silence, not a category")
                .containsExactlyInAnyOrder("n1", "n2", "n3", "n4", "n8", "n9");
        assertThat(groups.get("BLOG")).containsExactlyInAnyOrder("n5", "n6", "n7");
    }

    @Test
    void aConceptTheRoleCannotBeAboutMayNotSplitItsBucket() {
        assertThat(ConceptSplit.plausibleFor("DESIGN", "LAYOUT")).isTrue();
        assertThat(ConceptSplit.plausibleFor("DESIGN", "REVIEW"))
                .as("the observation about the wording is true, and it is not about the work")
                .isFalse();
        assertThat(ConceptSplit.plausibleFor("VIDEOGRAPHY", "SHOOT"))
                .as("an unmapped role permits nothing — the safe direction")
                .isFalse();

        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("n1", "LAYOUT");
        labels.put("n2", "LAYOUT");
        labels.put("n3", "LAYOUT");
        labels.put("n4", "REVIEW");
        labels.put("n5", "REVIEW");
        labels.put("n6", "REVIEW");

        assertThat(ConceptSplit.split(labels, "DESIGN", 3))
                .as("only one concept survives ADR-011, so ADR-010 then keeps the bucket whole")
                .hasSize(1);
    }

    @Test
    void aLabelDrawnFromWhatPeopleWroteIsAccepted() {
        List<String> written = List.of("I will brighten them and send a new version", "the header images for Aurora");

        assertThat(LabelGrounding.groundedLabel("brighten the images", written)).contains("brighten the images");
        assertThat(LabelGrounding.groundedLabel("review the header", written))
                .as("'review' is one of the four permitted generics")
                .contains("review the header");
    }

    @Test
    void aLabelContainingAnInventedWordIsRefused() {
        List<String> written = List.of("I will brighten them and send a new version");

        assertThat(LabelGrounding.groundedLabel("client onboarding", written))
                .as("fluent, plausible, and not what anybody wrote")
                .isEmpty();
        assertThat(LabelGrounding.groundedLabel("brighten the photographs before publishing anything", written))
                .as("and a label longer than four words is a sentence, not a step name")
                .isEmpty();
    }

    @Test
    void aDescriptionMayNotQuoteTheMessagesEvenThoughALabelMay() {
        List<String> written = List.of("their card was declined on the ad account this morning");

        assertThat(LabelGrounding.descriptionIsGenerated("Drafted from 4 pieces of work across 3 jobs.", written))
                .as("says what it was drawn from rather than repeating what somebody said")
                .isTrue();

        assertThat(LabelGrounding.descriptionIsGenerated(
                        "Handle it when their card was declined on the ad account.", written))
                .as("four consecutive words lifted from a message is a quotation, however it was arrived at")
                .isFalse();
    }

    private static Judgement.Verdict verdict(String value, double confidence) {
        return new Judgement.Verdict(value, confidence, "because");
    }
}
