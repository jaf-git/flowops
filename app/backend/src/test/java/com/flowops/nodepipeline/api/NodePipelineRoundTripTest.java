package com.flowops.nodepipeline.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.nodepipeline.application.port.WorkJudgePort;
import com.flowops.nodepipeline.domain.ai.Judgement;
import com.flowops.support.CompanyScenarioTest;
import com.flowops.support.RoundTripClient;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("NODEPIPE-RUN-01")
@Tag("roundtrip")
@Import(NodePipelineRoundTripTest.AModelThatAlwaysAnswers.class)
class NodePipelineRoundTripTest extends CompanyScenarioTest {
    @TestConfiguration
    static class AModelThatAlwaysAnswers {
        @Bean
        @Primary
        WorkJudgePort aModelNobodyShouldActOnInCompare() {
            return new WorkJudgePort() {
                @Override
                public boolean isAvailable() {
                    return true;
                }

                @Override
                public boolean isEnabled(Judgement.PlugPoint plugPoint) {
                    return plugPoint == Judgement.PlugPoint.SAME_WORK;
                }

                @Override
                public java.util.Optional<Judgement.Verdict> judge(Judgement.Question question) {
                    return java.util.Optional.of(new Judgement.Verdict("SAME", 0.95, "the words agree"));
                }

                @Override
                public int callsRemaining() {
                    return 200;
                }

                @Override
                public String modelId() {
                    return "stub-model";
                }

                @Override
                public String promptVersion() {
                    return "test";
                }
            };
        }
    }

    private UUID job;

    @BeforeEach
    void aWeekOfMarkedWork() throws Exception {
        buildTheCompany();

        UUID maria = jdbc.queryForObject("select id from auth_user where email = ?", UUID.class, OWNER_EMAIL);
        job = UUID.randomUUID();

        jdbc.update(
                """
                insert into job (id, name, status, standing, opened_at, closed_at, opened_by,
                                 last_activity_at, shape_eligible, is_rework)
                values (?, 'Aurora Coffee summer menu', 'CLOSED', false, ?, ?, ?, ?, true, false)
                """,
                job,
                Timestamp.from(Instant.now().minus(30, ChronoUnit.DAYS)),
                Timestamp.from(Instant.now().minus(2, ChronoUnit.DAYS)),
                maria,
                Timestamp.from(Instant.now().minus(2, ChronoUnit.DAYS)));

        mark("Aurora Coffee summer menu", "JOB_START", "STANDALONE", maria);
        mark("Where are the grocer captions?", "WORK", "QUERY", maria);
        mark("Same here. Quiet week for once.", "WORK", "STANDALONE", maria);
        mark("I will brighten them and send a new version of the header images", "WORK", "STANDALONE", maria);
    }

    @Test
    void anOwnerCanRunThePipelineAndTheRunRecordsWhatItRead() throws Exception {
        ResponseEntity<String> started = browser.post("/api/node-pipeline/runs", "");
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode run = json.readTree(started.getBody());

        assertThat(run.get("signature").asText())
                .as("64 hex characters, so two runs of the same rules are recognisably the same rules")
                .hasSize(64);
        assertThat(run.get("nodesRead").asInt())
                .as("the four marks this test made")
                .isGreaterThanOrEqualTo(4);
        assertThat(run.get("jobsRead").asInt()).isGreaterThanOrEqualTo(1);

        assertThat(run.get("tiers").fieldNames().hasNext())
                .as("every node reached some outcome, and ABSTAIN is one")
                .isTrue();
    }

    @Test
    void aComparedRunIsReadableAndARunThatWasNotComparedAnswersNoContent() throws Exception {
        JsonNode deterministic =
                json.readTree(browser.post("/api/node-pipeline/runs", "").getBody());

        assertThat(browser.get("/api/node-pipeline/runs/"
                                + deterministic.get("runId").asText() + "/comparison")
                        .getStatusCode())
                .as("this run was never in COMPARE, and saying so is not the same as answering zeros")
                .isEqualTo(HttpStatus.NO_CONTENT);

        JsonNode compared = json.readTree(
                browser.post("/api/node-pipeline/runs?aiMode=COMPARE", "").getBody());

        ResponseEntity<String> read =
                browser.get("/api/node-pipeline/runs/" + compared.get("runId").asText() + "/comparison");
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode buckets = json.readTree(read.getBody());
        assertThat(buckets.get("aiMode").asText()).isEqualTo("COMPARE");
        assertThat(buckets.get("compared").asInt())
                .as("every node this test marked was scored twice")
                .isGreaterThanOrEqualTo(4);

        assertThat(buckets.get("agreed").asInt())
                .isEqualTo(buckets.get("compared").asInt());
        assertThat(buckets.get("raised").asInt()).isZero();
        assertThat(buckets.get("lowered").asInt()).isZero();
        assertThat(buckets.get("changed").asInt()).isZero();
        assertThat(buckets.get("failed").asInt()).isZero();
    }

    @Test
    void aComparedRunReportsWhatTheRulesDecidedRatherThanWhatTheModelSaid() throws Exception {
        JsonNode compared = json.readTree(
                browser.post("/api/node-pipeline/runs?aiMode=COMPARE", "").getBody());
        String runId = compared.get("runId").asText();

        Map<String, Integer> fromTheRows = new java.util.LinkedHashMap<>();
        jdbc.query(
                """
                select deterministic_outcome, count(*) as howMany
                  from pipeline_decision
                 where run_id = ?::uuid and finding_kind = 'NODE_MATCH'
                 group by deterministic_outcome
                """,
                row -> {
                    fromTheRows.put(row.getString("deterministic_outcome"), row.getInt("howMany"));
                },
                runId);

        assertThat(fromTheRows)
                .as("a COMPARE run writes one row per node scored, and there were nodes to score")
                .isNotEmpty();

        Map<String, Integer> fromTheRun = new java.util.LinkedHashMap<>();
        compared.get("tiers")
                .fields()
                .forEachRemaining(
                        tier -> fromTheRun.put(tier.getKey(), tier.getValue().asInt()));

        assertThat(fromTheRun)
                .as("the tallies come from the verdict the loop kept; the column comes from the plain "
                        + "matcher. They agree only if COMPARE kept the plain one")
                .isEqualTo(fromTheRows);
    }

    @Test
    void runningTwiceProducesTheSameSignatureAndTwoDistinctRuns() throws Exception {
        JsonNode first =
                json.readTree(browser.post("/api/node-pipeline/runs", "").getBody());
        JsonNode second =
                json.readTree(browser.post("/api/node-pipeline/runs", "").getBody());

        assertThat(second.get("signature").asText())
                .as("nothing about the rules changed between them")
                .isEqualTo(first.get("signature").asText());
        assertThat(second.get("runId").asText())
                .as("but they are two observations, not one — collapsing them would destroy the history")
                .isNotEqualTo(first.get("runId").asText());
    }

    @Test
    void theHistoryListsPastRunsNewestFirstWithTheirSignatures() throws Exception {
        JsonNode first =
                json.readTree(browser.post("/api/node-pipeline/runs", "").getBody());
        JsonNode second =
                json.readTree(browser.post("/api/node-pipeline/runs", "").getBody());

        JsonNode history = json.readTree(browser.get("/api/node-pipeline/runs").getBody());

        assertThat(history.size()).as("both runs are in it").isGreaterThanOrEqualTo(2);
        assertThat(history.get(0).get("id").asText())
                .as("newest first, so the run somebody just started is the one they meet")
                .isEqualTo(second.get("runId").asText());
        assertThat(history.get(0).get("signature").asText())
                .isEqualTo(first.get("signature").asText());
    }

    @Test
    void twoIdenticalRunsProduceAnEmptyDiff() throws Exception {
        JsonNode first =
                json.readTree(browser.post("/api/node-pipeline/runs", "").getBody());
        JsonNode second =
                json.readTree(browser.post("/api/node-pipeline/runs", "").getBody());

        ResponseEntity<String> diff = browser.get("/api/node-pipeline/runs/%s/diff/%s"
                .formatted(first.get("runId").asText(), second.get("runId").asText()));

        assertThat(diff.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(diff.getBody()))
                .as("same corpus, same rules — nothing moved, and the diff must not invent movement")
                .isEmpty();
    }

    @Test
    void anItemStuckInOnlyOneRunAppearsWithTheOtherSideNull() throws Exception {
        JsonNode first =
                json.readTree(browser.post("/api/node-pipeline/runs", "").getBody());
        JsonNode second =
                json.readTree(browser.post("/api/node-pipeline/runs", "").getBody());

        UUID later = UUID.fromString(second.get("runId").asText());
        String vanished = jdbc.queryForObject(
                "select item_id from pipeline_item_stage where run_id = ? order by item_id limit 1",
                String.class,
                later);
        jdbc.update("delete from pipeline_item_stage where run_id = ? and item_id = ?", later, vanished);

        JsonNode diff = json.readTree(browser.get("/api/node-pipeline/runs/%s/diff/%s"
                        .formatted(first.get("runId").asText(), later))
                .getBody());

        assertThat(diff.size()).as("exactly the one item that changed").isEqualTo(1);
        assertThat(diff.get(0).get("itemId").asText()).isEqualTo(vanished);
        assertThat(diff.get(0).get("beforeStage").asText())
                .as("it stopped in the earlier run")
                .isNotEmpty();
        assertThat(diff.get(0).get("afterStage").isNull())
                .as("and the later run recorded nothing for it — which is not the same as saying it passed")
                .isTrue();
    }

    @Test
    void everyDroppedItemIsAttributableToAStageAndAReason() throws Exception {
        browser.post("/api/node-pipeline/runs", "");

        ResponseEntity<String> stuck = browser.get("/api/node-pipeline/runs/latest/stuck");
        assertThat(stuck.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode groups = json.readTree(stuck.getBody());
        assertThat(groups.isArray()).isTrue();
        assertThat(groups.size())
                .as("the boundary, the question and the chatter all stopped")
                .isPositive();

        for (JsonNode group : groups) {
            assertThat(group.get("lastStage").asText()).isNotBlank();
            assertThat(group.get("reason").asText())
                    .as("machine-readable, and the same string the matcher put on its verdict")
                    .isNotBlank();
            assertThat(group.get("count").asInt()).isPositive();
            assertThat(group.get("itemId").asText())
                    .as("one example travels with each group, so a person can look at a real case")
                    .isNotBlank();
        }
    }

    @Test
    void theLastRunReadsBackWithItsTallies() throws Exception {
        browser.post("/api/node-pipeline/runs", "");

        JsonNode latest =
                json.readTree(browser.get("/api/node-pipeline/runs/latest").getBody());

        assertThat(latest.get("run").get("signature").asText()).hasSize(64);
        assertThat(latest.get("run").get("nodesRead").asInt()).isGreaterThanOrEqualTo(4);
        assertThat(latest.get("run").get("reachedStage").asText())
                .as("the run reached CORRELATE — it matched, grouped and clustered")
                .isEqualTo("CORRELATE");
        assertThat(latest.get("stages").isArray()).isTrue();
    }

    @Test
    void findingsExcludeTheAbstentionsThatAreNeverthelessStored() throws Exception {
        browser.post("/api/node-pipeline/runs", "");

        JsonNode findings = json.readTree(browser.get("/api/node-pipeline/runs/latest/findings?kind=NODE_MATCH")
                .getBody());

        assertThat(findings.isArray()).isTrue();
        for (JsonNode finding : findings) {
            assertThat(finding.get("outcome").asText())
                    .as("an abstention is stored and is never shown as a finding")
                    .isNotEqualTo("ABSTAIN");
        }

        Integer stored = jdbc.queryForObject(
                "select count(*) from pipeline_decision where deterministic_outcome = 'ABSTAIN'", Integer.class);
        assertThat(stored)
                .as("but they ARE in the table — precision is measurable from what the pipeline said, "
                        + "recall only from what it did not")
                .isPositive();
    }

    @Test
    void aWindowThatRunsBackwardsIsRefusedBeforeAnythingIsWritten() throws Exception {
        Integer runsBefore = jdbc.queryForObject("select count(*) from analysis_run", Integer.class);

        ResponseEntity<String> refused =
                browser.post("/api/node-pipeline/runs?from=2026-09-01T00:00:00Z&to=2026-08-01T00:00:00Z", "");

        assertThat(refused.getStatusCode())
                .as("the window is the caller's input, so the use case refuses it rather than the "
                        + "database breaking on the run's first insert and answering INTERNAL_ERROR")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody()).contains("WINDOW_RUNS_BACKWARDS");

        assertThat(jdbc.queryForObject("select count(*) from analysis_run", Integer.class))
                .as("and no run was opened for it")
                .isEqualTo(runsBefore);

        assertThat(browser.get("/api/node-pipeline/runs/preview" + "?from=2026-09-01T00:00:00Z&to=2026-08-01T00:00:00Z")
                        .getStatusCode())
                .as("the preview reads the same window, so it refuses the same way rather than "
                        + "answering a confident zero")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aWindowThatBeginsAndEndsAtTheSameInstantIsRefusedToo() throws Exception {
        assertThat(browser.post("/api/node-pipeline/runs" + "?from=2026-09-01T00:00:00Z&to=2026-09-01T00:00:00Z", "")
                        .getStatusCode())
                .as("analysis_run's own constraint is window_to > window_from, and the refusal "
                        + "matches it rather than being one instant looser")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void anEmployeeCannotStartARunAndTheOwnerCan() throws Exception {
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        assertThat(andrei.post("/api/node-pipeline/runs", "").getStatusCode())
                .as("starting a run reads the whole graph; it is not an employee's act")
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(browser.post("/api/node-pipeline/runs", "").getStatusCode())
                .as("and the owner holds it — a permission granted to nobody would fail here too")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void noRouteInTheFeatureAnswersWithoutASession() throws Exception {
        RoundTripClient anonymous = new RoundTripClient(rest);

        for (String route : java.util.List.of(
                "/api/node-pipeline/runs/latest",
                "/api/node-pipeline/runs/latest/stuck",
                "/api/node-pipeline/runs/latest/findings")) {
            assertThat(anonymous.get(route).getStatusCode()).as("%s", route).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    void everySourceOfADecisionComesBackNamed() throws Exception {
        assertThat(browser.post("/api/node-pipeline/runs", "").getStatusCode()).isEqualTo(HttpStatus.OK);

        UUID runId = jdbc.queryForObject(
                "select id from analysis_run where signature is not null order by started_at desc limit 1", UUID.class);

        UUID named = markReturningId("Brightened the header images and sent the new version.", "Header images");
        UUID unnamed = markReturningId("Rewrote the opening paragraph of the summer menu copy.", null);

        UUID decision = UUID.randomUUID();
        jdbc.update(
                """
                insert into pipeline_decision (id, run_id, finding_kind, subject, deterministic_outcome,
                                               score, reason, created_at)
                values (?, ?, 'STEP_KIND', 'K:CONTENT', 'NUDGE', 0.8, '2 marks across 1 jobs', now())
                """,
                decision,
                runId);
        subject(decision, "NODE", named);
        subject(decision, "NODE", unnamed);
        subject(decision, "JOB", job);

        JsonNode sources = json.readTree(browser.get("/api/node-pipeline/runs/decisions/" + decision + "/sources")
                .getBody());

        assertThat(sources)
                .as("three rows were written and three must come back")
                .hasSize(3);

        assertThat(labelOf(sources, named)).isEqualTo("Header images");

        assertThat(labelOf(sources, unnamed)).isEqualTo("Rewrote the opening paragraph of the summer menu copy.");

        assertThat(labelOf(sources, job)).isEqualTo("Aurora Coffee summer menu");
        assertThat(detailOf(sources, job)).isEqualTo("CLOSED");
    }

    @Test
    void aDecisionThatNamesItsOwnSubjectRestsOnNothingSeparate() throws Exception {
        assertThat(browser.post("/api/node-pipeline/runs", "").getStatusCode()).isEqualTo(HttpStatus.OK);

        UUID jobMatch = jdbc.queryForObject(
                "select id from pipeline_decision where finding_kind = 'JOB_MATCH' limit 1", UUID.class);

        JsonNode sources = json.readTree(browser.get("/api/node-pipeline/runs/decisions/" + jobMatch + "/sources")
                .getBody());

        assertThat(sources).isEmpty();
    }

    private static String labelOf(JsonNode sources, UUID id) {
        for (JsonNode source : sources) {
            if (source.get("id").asText().equals(id.toString())) {
                return source.get("label").asText();
            }
        }
        throw new AssertionError("no source row for " + id + "; the union dropped one of the three kinds");
    }

    private static String detailOf(JsonNode sources, UUID id) {
        for (JsonNode source : sources) {
            if (source.get("id").asText().equals(id.toString())) {
                return source.get("detail").asText();
            }
        }
        throw new AssertionError("no source row for " + id);
    }

    private void subject(UUID decision, String kind, UUID id) {
        jdbc.update(
                "insert into pipeline_decision_subject (decision_id, subject_kind, subject_id) values (?, ?, ?)",
                decision,
                kind,
                id);
    }

    private UUID markReturningId(String text, String title) {
        UUID id = UUID.randomUUID();
        UUID creator = jdbc.queryForObject("select id from auth_user where email = ?", UUID.class, OWNER_EMAIL);
        jdbc.update(
                """
                insert into work_node (id, job_id, text, title, creator_id, created_at, state, direction,
                                       kind, work_type, output_type)
                values (?, ?, ?, ?, ?, ?, 'COMPLETED', 'STANDALONE', 'WORK', 'CONTENT', 'TEXT')
                """,
                id,
                job,
                text,
                title,
                creator,
                Timestamp.from(Instant.now().minus(5, ChronoUnit.DAYS)));
        return id;
    }

    private void mark(String text, String kind, String direction, UUID creator) {
        jdbc.update(
                """
                insert into work_node (id, job_id, text, creator_id, created_at, state, direction, kind,
                                       work_type, output_type)
                values (?, ?, ?, ?, ?, 'COMPLETED', ?, ?, 'CONTENT', 'TEXT')
                """,
                UUID.randomUUID(),
                job,
                text,
                creator,
                Timestamp.from(Instant.now().minus(5, ChronoUnit.DAYS)),
                direction,
                kind);
    }
}
