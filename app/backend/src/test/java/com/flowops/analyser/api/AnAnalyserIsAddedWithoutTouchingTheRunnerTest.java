package com.flowops.analyser.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.analyser.domain.Absence;
import com.flowops.analyser.domain.Analyser;
import com.flowops.analyser.domain.AnalyserStage;
import com.flowops.analyser.domain.Category;
import com.flowops.analyser.domain.Clean;
import com.flowops.analyser.domain.Confidence;
import com.flowops.analyser.domain.Finding;
import com.flowops.analyser.domain.Precondition;
import com.flowops.analyser.domain.Report;
import com.flowops.analyser.domain.Severity;
import com.flowops.analyser.domain.Snapshot;
import com.flowops.analyser.domain.SubjectKind;
import com.flowops.support.CompanyScenarioTest;
import com.flowops.support.RoundTripClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("ANALYSER-RUN-01")
@Import(AnAnalyserIsAddedWithoutTouchingTheRunnerTest.ThreeAnalysersNobodyRegistered.class)
class AnAnalyserIsAddedWithoutTouchingTheRunnerTest extends CompanyScenarioTest {
    @TestConfiguration
    static class ThreeAnalysersNobodyRegistered {
        @Bean
        Analyser theOneThatAnswers() {
            return new Analyser() {
                @Override
                public AnalyserStage stage() {
                    return AnalyserStage.DETECT;
                }

                @Override
                public String id() {
                    return "T1_ANSWERS";
                }

                @Override
                public Category category() {
                    return Category.YOUR_WORK;
                }

                @Override
                public Report analyse(Snapshot snapshot) {
                    Finding finding = new Finding(
                            id(),
                            "nodes_in_window",
                            SubjectKind.WORKSPACE,
                            "workspace",
                            Category.YOUR_WORK,
                            "The window held %d units of work"
                                    .formatted(snapshot.nodes().size()),
                            List.of("Counted from the one snapshot every analyser in this run read."),
                            Map.of(),
                            Severity.LOW,
                            Confidence.HIGH,
                            snapshot.nodes().size(),
                            snapshot.nodes().size(),
                            "Look at the window");
                    return new Report(
                            id(),
                            snapshot.nodes().size(),
                            List.of(finding),
                            List.of(new Absence("no_title", "Nodes marked before V92 carry no title.", false)),
                            List.of(),
                            List.of(Precondition.met("a snapshot", "one snapshot, shared")));
                }
            };
        }

        @Bean
        Analyser theOneThatIsBlocked() {
            return new Analyser() {
                @Override
                public AnalyserStage stage() {
                    return AnalyserStage.DETECT;
                }

                @Override
                public String id() {
                    return "T2_BLOCKED";
                }

                @Override
                public Category category() {
                    return Category.YOUR_PROCESSES;
                }

                @Override
                public Report analyse(Snapshot snapshot) {
                    return Report.blocked(
                            id(),
                            snapshot.brackets().size(),
                            Precondition.unmet("at least 3 finished engagements", "you have 0", "Close an engagement"));
                }
            };
        }

        @Bean
        Analyser theOneThatIsClean() {
            return new Analyser() {
                @Override
                public AnalyserStage stage() {
                    return AnalyserStage.DETECT;
                }

                @Override
                public String id() {
                    return "T4_CLEAN";
                }

                @Override
                public Category category() {
                    return Category.YOUR_LIBRARY;
                }

                @Override
                public Report analyse(Snapshot snapshot) {
                    return Report.clean(
                            id(),
                            snapshot.templates().size(),
                            new Clean(
                                    "every_template_matched",
                                    "%d approved templates checked; every one has matched work."
                                            .formatted(snapshot.templates().size())));
                }
            };
        }

        @Bean
        Analyser theOneThatThrows() {
            return new Analyser() {
                @Override
                public AnalyserStage stage() {
                    return AnalyserStage.DETECT;
                }

                @Override
                public String id() {
                    return "T3_THROWS";
                }

                @Override
                public Category category() {
                    return Category.YOUR_TIME;
                }

                @Override
                public Report analyse(Snapshot snapshot) {
                    throw new IllegalStateException("this analyser is broken on purpose");
                }
            };
        }
    }

    private RoundTripClient owner() {
        return signedInBrowser("ionut@atelier.ro");
    }

    private JsonNode analyser(JsonNode run, String id) {
        for (JsonNode candidate : run.get("analysers")) {
            if (candidate.get("id").asText().equals(id)) {
                return candidate;
            }
        }
        throw new AssertionError("no analyser " + id + " on the run; the runner did not pick it up");
    }

    @Test
    void ananalyserDeclaredNowhereInTheRunnerStillRunsAndStillReports() throws Exception {
        buildTheCompany();
        RoundTripClient ionut = owner();

        ResponseEntity<String> ran = ionut.post("/api/analysis/runs", null);
        assertThat(ran.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode run = json.readTree(ran.getBody());
        assertThat(run.get("runId").asText()).isNotBlank();
        assertThat(run.get("windowFrom").asText()).isNotBlank();
        assertThat(run.get("windowTo").asText()).isNotBlank();

        assertThat(analyser(run, "T1_ANSWERS").get("findings").asInt()).isEqualTo(1);
        assertThat(analyser(run, "T2_BLOCKED").get("findings").asInt()).isZero();

        assertThat(analyser(run, "T3_THROWS").get("failure").asText()).contains("IllegalStateException");
    }

    @Test
    void theDiagnosticSaysWhatEachAnalyserReadAndWhatItCouldNotSee() throws Exception {
        buildTheCompany();
        RoundTripClient ionut = owner();
        assertThat(ionut.post("/api/analysis/runs", null).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> latest = ionut.get("/api/analysis/runs/latest");
        assertThat(latest.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode run = json.readTree(latest.getBody());

        JsonNode answered = analyser(run, "T1_ANSWERS");
        assertThat(answered.get("absences")).hasSize(1);
        assertThat(answered.get("absences").get(0).get("what").asText()).isEqualTo("no_title");
        assertThat(answered.get("absences").get(0).get("blocking").asBoolean()).isFalse();

        JsonNode blocked = analyser(run, "T2_BLOCKED");
        assertThat(blocked.get("preconditions")).hasSize(1);
        assertThat(blocked.get("preconditions").get(0).get("met").asBoolean()).isFalse();
        assertThat(blocked.get("preconditions").get(0).get("had").asText()).isEqualTo("you have 0");

        assertThat(blocked.get("itemsRead").isInt()).isTrue();
    }

    @Test
    void aFindingSeenTwiceIsNotNewTheSecondTime() throws Exception {
        buildTheCompany();
        RoundTripClient ionut = owner();

        String firstRun = runAndReturnItsId(ionut);
        String secondRun = runAndReturnItsId(ionut);

        assertThat(lifecycleIn(secondRun)).isEqualTo("STILL_TRUE").isNotEqualTo("NEW");

        assertThat(firstSeenIn(secondRun)).isEqualTo(firstSeenIn(firstRun));
    }

    private String runAndReturnItsId(RoundTripClient who) throws Exception {
        ResponseEntity<String> ran = who.post("/api/analysis/runs", null);
        assertThat(ran.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(ran.getBody()).get("runId").asText();
    }

    private String lifecycleIn(String runId) {
        return jdbc.queryForObject(
                """
                select lifecycle from analysis_finding
                 where run_id = ?::uuid and detector = 'T1_ANSWERS:nodes_in_window'
                """,
                String.class,
                runId);
    }

    private java.sql.Timestamp firstSeenIn(String runId) {
        return jdbc.queryForObject(
                """
                select first_seen_at from analysis_finding
                 where run_id = ?::uuid and detector = 'T1_ANSWERS:nodes_in_window'
                """,
                java.sql.Timestamp.class,
                runId);
    }

    @Test
    void ananalyserThatEmitsNothingAtAllFailsTheRunWithoutLosingTheOthers() throws Exception {
        buildTheCompany();
        RoundTripClient ionut = owner();

        String runId = runAndReturnItsId(ionut);

        String failure =
                jdbc.queryForObject("select failure from analysis_run where id = ?::uuid", String.class, runId);
        assertThat(failure).isNull();

        JsonNode run = json.readTree(ionut.get("/api/analysis/runs/latest").getBody());
        JsonNode clean = analyser(run, "T4_CLEAN");
        assertThat(clean.get("clean")).hasSize(1);
        assertThat(clean.get("clean").get(0).get("what").asText()).isEqualTo("every_template_matched");
        assertThat(clean.get("findings").asInt()).isZero();
    }

    @Test
    void beforeAnythingHasRunTheAnswerIsNoContentRatherThanAnEmptyRun() throws Exception {
        buildTheCompany();

        ResponseEntity<String> latest = owner().get("/api/analysis/runs/latest");

        assertThat(latest.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
