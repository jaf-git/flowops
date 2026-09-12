package com.flowops.nodepipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.nodepipeline.domain.RunSignature;
import com.flowops.nodepipeline.domain.match.Lexicons;
import com.flowops.nodepipeline.domain.match.MatchWeights;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RunSignatureTest {
    private static final Lexicons LEXICONS = new Lexicons(
            Map.of(Lexicons.RolePair.of("Designer", "Junior designer"), 0.7),
            Map.of("PHOTO", List.of("photo", "shoot")));

    @Test
    void thesameConfigurationSignsIdenticallyEveryTime() {
        String once = signature(MatchWeights.reference());
        String again = signature(MatchWeights.reference());

        assertThat(once).isEqualTo(again).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void theOrderTheVocabularyHappenedToLoadInIsNotPartOfTheConfiguration() {
        Map<String, List<String>> oneOrder = new LinkedHashMap<>();
        oneOrder.put("PHOTO", List.of("shoot", "photo"));
        oneOrder.put("VIDEO", List.of("reel"));

        Map<String, List<String>> otherOrder = new LinkedHashMap<>();
        otherOrder.put("VIDEO", List.of("reel"));
        otherOrder.put("PHOTO", List.of("photo", "shoot"));

        assertThat(RunSignature.of(MatchWeights.reference(), new Lexicons(Map.of(), oneOrder), "OFF", null, null))
                .isEqualTo(RunSignature.of(
                        MatchWeights.reference(), new Lexicons(Map.of(), otherOrder), "OFF", null, null));
    }

    @Test
    void everyThresholdThatCouldChangeAnAnswerChangesTheSignature() {
        MatchWeights base = MatchWeights.reference();
        String baseline = signature(base);

        assertThat(signature(with(base, b -> b.withScoreFloor(0.75)))).isNotEqualTo(baseline);
        assertThat(signature(with(base, b -> b.withTextVeto(0.55)))).isNotEqualTo(baseline);
        assertThat(signature(with(base, b -> b.withTextWeight(0.55)))).isNotEqualTo(baseline);
        assertThat(signature(with(base, b -> b.withFinalists(6)))).isNotEqualTo(baseline);
        assertThat(signature(with(base, b -> b.withKeywordCorroboration(0.4)))).isNotEqualTo(baseline);
    }

    @Test
    void togglingAGateChangesTheSignature() {
        MatchWeights base = MatchWeights.reference();
        MatchWeights.Gates gates = base.gates();

        MatchWeights withoutIntent = new MatchWeights(
                base.textWeight(),
                base.roleWeight(),
                base.shapeWeight(),
                base.outputWeight(),
                base.scoreFloor(),
                base.confidenceFloor(),
                base.separationMinimum(),
                base.textVeto(),
                base.textQualityFloor(),
                base.finalists(),
                base.plausibleFloor(),
                base.uniqueFloor(),
                base.keywordCorroboration(),
                new MatchWeights.Gates(
                        gates.boundary(),
                        gates.closure(),
                        false,
                        gates.direction(),
                        gates.postClose(),
                        gates.shapeFeature(),
                        gates.outputMap(),
                        gates.keywords(),
                        gates.typeContradiction(),
                        gates.markerAddress()));

        assertThat(signature(withoutIntent)).isNotEqualTo(signature(base));
    }

    @Test
    void theModelAndThePromptAreBothPartOfTheRules() {
        MatchWeights base = MatchWeights.reference();
        String off = RunSignature.of(base, LEXICONS, "OFF", null, null);
        String on = RunSignature.of(base, LEXICONS, "ON", "llama3.2:3b", "v1");
        String newerPrompt = RunSignature.of(base, LEXICONS, "ON", "llama3.2:3b", "v2");
        String biggerModel = RunSignature.of(base, LEXICONS, "ON", "llama3.1:8b", "v1");

        assertThat(List.of(off, on, newerPrompt, biggerModel)).doesNotHaveDuplicates();
    }

    @Test
    void changingTheVocabularyChangesTheSignature() {
        String withPhoto = RunSignature.of(MatchWeights.reference(), LEXICONS, "OFF", null, null);
        String withPhotoAndVideo = RunSignature.of(
                MatchWeights.reference(),
                new Lexicons(
                        LEXICONS.roleKinship(), Map.of("PHOTO", List.of("photo", "shoot"), "VIDEO", List.of("reel"))),
                "OFF",
                null,
                null);

        assertThat(withPhoto).isNotEqualTo(withPhotoAndVideo);
    }

    private static String signature(MatchWeights weights) {
        return RunSignature.of(weights, LEXICONS, "OFF", null, null);
    }

    private static MatchWeights with(MatchWeights base, java.util.function.UnaryOperator<Builder> change) {
        return change.apply(new Builder(base)).build();
    }

    private record Builder(
            MatchWeights base,
            Double scoreFloor,
            Double textVeto,
            Double textWeight,
            Integer finalists,
            Double keywordCorroboration) {
        Builder(MatchWeights base) {
            this(base, null, null, null, null, null);
        }

        Builder withScoreFloor(double value) {
            return new Builder(base, value, textVeto, textWeight, finalists, keywordCorroboration);
        }

        Builder withTextVeto(double value) {
            return new Builder(base, scoreFloor, value, textWeight, finalists, keywordCorroboration);
        }

        Builder withTextWeight(double value) {
            return new Builder(base, scoreFloor, textVeto, value, finalists, keywordCorroboration);
        }

        Builder withFinalists(int value) {
            return new Builder(base, scoreFloor, textVeto, textWeight, value, keywordCorroboration);
        }

        Builder withKeywordCorroboration(double value) {
            return new Builder(base, scoreFloor, textVeto, textWeight, finalists, value);
        }

        MatchWeights build() {
            return new MatchWeights(
                    textWeight == null ? base.textWeight() : textWeight,
                    base.roleWeight(),
                    base.shapeWeight(),
                    base.outputWeight(),
                    scoreFloor == null ? base.scoreFloor() : scoreFloor,
                    base.confidenceFloor(),
                    base.separationMinimum(),
                    textVeto == null ? base.textVeto() : textVeto,
                    base.textQualityFloor(),
                    finalists == null ? base.finalists() : finalists,
                    base.plausibleFloor(),
                    base.uniqueFloor(),
                    keywordCorroboration == null ? base.keywordCorroboration() : keywordCorroboration,
                    base.gates());
        }
    }
}
