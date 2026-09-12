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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("ANALYSER-VIEW-FINDINGS-01")
@Import(TheOwnerWorksAQueueTest.TwoFindingsInTwoCategories.class)
class TheOwnerWorksAQueueTest extends CompanyScenarioTest {
    private static final String GROWING = "T5_GROWING";
    private static final String SMALL = "T6_SMALL";

    @TestConfiguration
    static class TwoFindingsInTwoCategories {
        static String subject = "workspace";
        static int reachOfTheGrowingOne = 40;

        @Bean
        Analyser theGrowingOne() {
            return new Analyser() {
                @Override
                public AnalyserStage stage() {
                    return AnalyserStage.DETECT;
                }

                @Override
                public String id() {
                    return GROWING;
                }

                @Override
                public Category category() {
                    return Category.YOUR_LIBRARY;
                }

                @Override
                public Report analyse(Snapshot snapshot) {
                    Finding finding = new Finding(
                            id(),
                            "templates_nobody_matched",
                            SubjectKind.WORKSPACE,
                            subject,
                            category(),
                            "%d approved templates have never matched any work".formatted(reachOfTheGrowingOne),
                            List.of("Counted from the one snapshot every analyser in this run read."),
                            Map.of(),
                            Severity.HIGH,
                            Confidence.HIGH,
                            reachOfTheGrowingOne,
                            null,
                            "Review the library");
                    return new Report(id(), reachOfTheGrowingOne, List.of(finding), List.of(), List.of(), List.of());
                }
            };
        }

        @Bean
        Analyser theSmallOne() {
            return new Analyser() {
                @Override
                public AnalyserStage stage() {
                    return AnalyserStage.DETECT;
                }

                @Override
                public String id() {
                    return SMALL;
                }

                @Override
                public Category category() {
                    return Category.YOUR_TIME;
                }

                @Override
                public Report analyse(Snapshot snapshot) {
                    Finding finding = new Finding(
                            id(),
                            "one_slow_thing",
                            SubjectKind.WORK_TYPE,
                            subject,
                            category(),
                            "One kind of work is slower than the rest",
                            List.of("Three brackets, and they are the long ones."),
                            Map.of(),
                            Severity.LOW,
                            Confidence.LOW,
                            3,
                            100,
                            "Look at it");
                    return new Report(id(), 3, List.of(finding), List.of(), List.of(), List.of());
                }
            };
        }
    }

    @BeforeEach
    void eachTestGetsItsOwnFindings(TestInfo which) {
        TwoFindingsInTwoCategories.subject =
                which.getTestMethod().map(java.lang.reflect.Method::getName).orElse("workspace");
        TwoFindingsInTwoCategories.reachOfTheGrowingOne = 40;
    }

    private RoundTripClient owner() {
        return signedInBrowser("ionut@atelier.ro");
    }

    private void run(RoundTripClient who) {
        assertThat(who.post("/api/analysis/runs", null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private JsonNode queue(RoundTripClient who) throws Exception {
        ResponseEntity<String> response = who.get("/api/analysis/findings");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(response.getBody());
    }

    private static Stream<JsonNode> items(JsonNode queue) {
        List<JsonNode> all = new ArrayList<>();
        for (JsonNode group : queue.get("groups")) {
            for (JsonNode item : group.get("items")) {
                all.add(item);
            }
        }
        return all.stream();
    }

    private static Optional<JsonNode> itemFrom(JsonNode queue, String analyser) {
        return items(queue)
                .filter(item -> item.get("analyser").asText().equals(analyser))
                .findFirst();
    }

    @Test
    void theQueueIsGroupedByCategoryAndEveryItemSaysWhyItRanksWhereItDoes() throws Exception {
        buildTheCompany();
        RoundTripClient ionut = owner();
        run(ionut);

        JsonNode queue = queue(ionut);

        assertThat(queue.get("runId").asText()).isNotBlank();
        assertThat(queue.get("windowFrom").asText()).isNotBlank();

        JsonNode growing = itemFrom(queue, GROWING)
                .orElseThrow(() -> new AssertionError("the growing finding is not on the queue"));
        assertThat(growing.get("headline").asText()).contains("40 approved templates");
        assertThat(growing.get("whyItRanks").asText()).contains("High because it touches 40");
        assertThat(growing.get("action").asText()).isEqualTo("Review the library");
        assertThat(growing.get("reachOf").isNull()).isTrue();

        List<String> categories = new ArrayList<>();
        queue.get("groups")
                .forEach(group -> categories.add(group.get("category").asText()));
        assertThat(categories).contains("YOUR_LIBRARY", "YOUR_TIME").doesNotHaveDuplicates();
    }

    @Test
    void insideEveryGroupTheWorstIsFirst() throws Exception {
        buildTheCompany();
        RoundTripClient ionut = owner();
        run(ionut);

        for (JsonNode group : queue(ionut).get("groups")) {
            double previous = Double.MAX_VALUE;
            for (JsonNode item : group.get("items")) {
                double priority = item.get("priority").asDouble();
                assertThat(priority).isLessThanOrEqualTo(previous);
                previous = priority;
            }
        }
    }

    @Test
    void dismissingAFindingTakesItOffTheQueueImmediately() throws Exception {
        buildTheCompany();
        RoundTripClient ionut = owner();
        run(ionut);

        String id = itemFrom(queue(ionut), SMALL).orElseThrow().get("id").asText();

        assertThat(ionut.post("/api/analysis/findings/" + id + "/dismiss", null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        JsonNode after = queue(ionut);
        assertThat(itemFrom(after, SMALL)).isEmpty();

        assertThat(after.get("standing").get("dismissed").asInt()).isGreaterThan(0);
    }

    @Test
    void aDismissedFindingDoesNotComeBackOnTheNextRun() throws Exception {
        buildTheCompany();
        RoundTripClient ionut = owner();
        run(ionut);

        String id = itemFrom(queue(ionut), SMALL).orElseThrow().get("id").asText();
        ionut.post("/api/analysis/findings/" + id + "/dismiss", null);

        run(ionut);

        assertThat(itemFrom(queue(ionut), SMALL)).isEmpty();

        assertThat(newestLifecycleOf(SMALL)).isEqualTo("DISMISSED");
    }

    @Test
    void aDismissedFindingReturnsMarkedWorseOnceItHasGrownByHalf() throws Exception {
        buildTheCompany();
        RoundTripClient ionut = owner();

        run(ionut);
        String id = itemFrom(queue(ionut), GROWING).orElseThrow().get("id").asText();
        assertThat(ionut.post("/api/analysis/findings/" + id + "/dismiss", null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(itemFrom(queue(ionut), GROWING)).isEmpty();

        TwoFindingsInTwoCategories.reachOfTheGrowingOne = 70;
        run(ionut);

        JsonNode returned =
                itemFrom(queue(ionut), GROWING).orElseThrow(() -> new AssertionError("it did not come back at 70"));
        assertThat(returned.get("lifecycle").asText()).isEqualTo("WORSENING");
        assertThat(returned.get("reach").asInt()).isEqualTo(70);
        assertThat(returned.get("whyItRanks").asText()).contains("worse than last run");
    }

    @Test
    void aDismissedFindingThatGrewALittleStaysDismissed() throws Exception {
        buildTheCompany();
        RoundTripClient ionut = owner();

        run(ionut);
        String id = itemFrom(queue(ionut), GROWING).orElseThrow().get("id").asText();
        ionut.post("/api/analysis/findings/" + id + "/dismiss", null);

        TwoFindingsInTwoCategories.reachOfTheGrowingOne = 48;
        run(ionut);

        assertThat(itemFrom(queue(ionut), GROWING)).isEmpty();
        assertThat(newestLifecycleOf(GROWING)).isEqualTo("DISMISSED");
    }

    @Test
    void dismissingSomethingThatIsNotThereIsNotFound() throws Exception {
        buildTheCompany();

        ResponseEntity<String> refused = owner().post("/api/analysis/findings/" + UUID.randomUUID() + "/dismiss", null);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("NOT_FOUND");
    }

    @Test
    void aRunWhereNothingMovedSaysSoRatherThanLookingIdentical() throws Exception {
        buildTheCompany();
        RoundTripClient ionut = owner();

        run(ionut);
        run(ionut);

        JsonNode standing = queue(ionut).get("standing");

        assertThat(standing.get("nothingNew").asBoolean()).isTrue();
        assertThat(standing.get("stillTrue").asInt()).isGreaterThan(0);
        assertThat(standing.get("shown").asInt()).isGreaterThan(0);
    }

    private String newestLifecycleOf(String analyser) {
        return jdbc.queryForObject(
                """
                select lifecycle from analysis_finding
                 where detector like ? and subject_key = ?
                 order by created_at desc limit 1
                """,
                String.class,
                analyser + ":%",
                TwoFindingsInTwoCategories.subject);
    }
}
