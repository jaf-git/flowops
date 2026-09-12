package com.flowops.analyser.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.analyser.domain.Analyser;
import com.flowops.analyser.domain.AnalyserStage;
import com.flowops.analyser.domain.Category;
import com.flowops.analyser.domain.Confidence;
import com.flowops.analyser.domain.Finding;
import com.flowops.analyser.domain.Report;
import com.flowops.analyser.domain.Severity;
import com.flowops.analyser.domain.Snapshot;
import com.flowops.analyser.domain.SubjectKind;
import com.flowops.support.CompanyScenarioTest;
import com.flowops.support.RoundTripClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("ANALYSER-RUN-01")
@Import(EveryFindingSaysWhichQuestionItAnsweredTest.TwoAnalysersAtTwoStages.class)
class EveryFindingSaysWhichQuestionItAnsweredTest extends CompanyScenarioTest {
    private static final String COUNTS_THINGS = "T1_COUNTS";
    private static final String PROPOSES_THINGS = "T2_PROPOSES";

    @TestConfiguration
    static class TwoAnalysersAtTwoStages {
        @Bean
        Analyser theOneThatMeasures() {
            return analyserSaying(COUNTS_THINGS, AnalyserStage.MEASURE, "counted_something");
        }

        @Bean
        Analyser theOneThatRecommends() {
            return analyserSaying(PROPOSES_THINGS, AnalyserStage.RECOMMEND, "proposed_something");
        }

        private static Analyser analyserSaying(String id, AnalyserStage stage, String kind) {
            return new Analyser() {
                @Override
                public String id() {
                    return id;
                }

                @Override
                public AnalyserStage stage() {
                    return stage;
                }

                @Override
                public Category category() {
                    return Category.YOUR_LIBRARY;
                }

                @Override
                public Report analyse(Snapshot snapshot) {
                    return new Report(
                            id,
                            1,
                            List.of(new Finding(
                                    id,
                                    kind,
                                    SubjectKind.WORKSPACE,
                                    "workspace",
                                    Category.YOUR_LIBRARY,
                                    "A finding from " + id,
                                    List.of("Because this analyser always says so."),
                                    Map.of(),
                                    Severity.LOW,
                                    Confidence.HIGH,
                                    1,
                                    1,
                                    "Do nothing")),
                            List.of(),
                            List.of(),
                            List.of());
                }
            };
        }
    }

    @Test
    @DisplayName("two analysers at two stages produce findings carrying two different stages")
    void theStageTravelsWithTheAnalyserThatWroteIt() throws Exception {
        buildTheCompany();
        RoundTripClient owner = signedInBrowser("ionut@atelier.ro");
        assertThat(owner.post("/api/analysis/runs", null).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> response = owner.get("/api/analysis/findings");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, String> stageByAnalyser = new HashMap<>();
        for (JsonNode group : json.readTree(response.getBody()).path("groups")) {
            for (JsonNode item : group.path("items")) {
                stageByAnalyser.put(
                        item.path("analyser").asText(), item.path("stage").asText());
            }
        }

        assertThat(stageByAnalyser)
                .as("both test analysers reached the queue, so there is something to judge")
                .containsKeys(COUNTS_THINGS, PROPOSES_THINGS);

        assertThat(stageByAnalyser.get(COUNTS_THINGS)).isEqualTo("MEASURE");
        assertThat(stageByAnalyser.get(PROPOSES_THINGS)).isEqualTo("RECOMMEND");

        assertThat(stageByAnalyser.values())
                .as("the stage is what each analyser answered, not one literal every row shares")
                .doesNotContain("ANALYSE");
    }
}
