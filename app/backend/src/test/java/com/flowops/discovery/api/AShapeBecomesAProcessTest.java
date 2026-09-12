package com.flowops.discovery.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.CompanyScenarioTest;
import com.flowops.support.RoundTripClient;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("DISCOVERY-WRITE-IT-DOWN-01")
class AShapeBecomesAProcessTest extends CompanyScenarioTest {
    private static final String SHAPE = "CONTENT → DESIGN → ADS";

    private record Executed(Long tasks, Long runs) {}

    private Executed whatTheApplicationHolds() {
        return new Executed(
                jdbc.queryForObject("select count(*) from task", Long.class),
                jdbc.queryForObject("select count(*) from process_instance", Long.class));
    }

    private Proposal aProposalToWriteDown(String kind, String subjectKind, String subjectKey) {
        UUID run = UUID.randomUUID();
        UUID finding = UUID.randomUUID();
        UUID proposal = UUID.randomUUID();
        Instant now = Instant.now();

        jdbc.update(
                """
                insert into analysis_run (
                    id, window_from, window_to, started_at, finished_at,
                    brackets_read, waits_read, reached_stage)
                values (?,?,?,?,?,?,?,'RECOMMEND')
                """,
                run,
                Timestamp.from(now.minusSeconds(2_592_000)),
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(now),
                25,
                4);

        jdbc.update(
                """
                insert into analysis_finding (
                    id, run_id, detector, stage, subject_kind, subject_key,
                    headline, sample_size, created_at)
                values (?,?,'recurring-shape','DETECT',?,?,?,?,?)
                """,
                finding,
                run,
                subjectKind,
                subjectKey,
                subjectKey + " ran the same way in 5 jobs",
                5,
                Timestamp.from(now));

        jdbc.update(
                """
                insert into analysis_recommendation (
                    id, run_id, finding_id, kind, headline, detail, confidence, created_at)
                values (?,?,?,?,?,null,'STRONG',?)
                """,
                proposal,
                run,
                finding,
                kind,
                "Write down " + subjectKey + " as a process",
                Timestamp.from(now));

        return new Proposal(proposal.toString(), finding.toString());
    }

    private record Proposal(String id, String findingId) {}

    private ResponseEntity<String> writeItDown(RoundTripClient who, String proposal, String name) {
        return who.post(
                "/api/discovery/analysis/recommendations/" + proposal + "/write-it-down",
                "{\"name\":\"%s\"}".formatted(name));
    }

    @Test
    void aShapeBecomesAProcessTemplateThatProcessItselfCanBeAskedFor() throws Exception {
        buildTheCompany();
        Proposal proposal = aProposalToWriteDown("WRITE_IT_DOWN", "SHAPE", SHAPE);
        Executed before = whatTheApplicationHolds();

        ResponseEntity<String> written = writeItDown(browser, proposal.id(), "Client campaign");

        assertThat(written.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode crossed = json.readTree(written.getBody());
        assertThat(crossed.get("name").asText()).isEqualTo("Client campaign");
        assertThat(crossed.get("steps"))
                .as("the steps are returned, because *done* is the one word nobody can check")
                .hasSize(3);
        assertThat(crossed.get("steps").get(0).asText()).isEqualTo("Content");
        assertThat(crossed.get("steps").get(2).asText()).isEqualTo("Ads");

        String template = crossed.get("processTemplateId").asText();
        ResponseEntity<String> fromTheLibrary = browser.get("/api/process-templates/" + template);

        assertThat(fromTheLibrary.getStatusCode())
                .as("read back through PROCESS's own door, because a returned identifier is not a row")
                .isEqualTo(HttpStatus.OK);
        JsonNode entry = json.readTree(fromTheLibrary.getBody());
        assertThat(entry.get("name").asText()).isEqualTo("Client campaign");
        assertThat(entry.get("steps"))
                .as("one step per work type in the shape, in the order the graph recorded them")
                .hasSize(3);

        assertThat(jdbc.queryForObject(
                        "select produced_template_id from analysis_recommendation where id = ?::uuid",
                        String.class,
                        proposal.id()))
                .as("the proposal records what the act produced, so a later reader can reach it")
                .isEqualTo(template);
        assertThat(jdbc.queryForObject(
                        "select subject_key from analysis_finding where id = ?::uuid",
                        String.class,
                        proposal.findingId()))
                .as("nothing was consumed — the finding still rests on the brackets it was drawn from")
                .isEqualTo(SHAPE);

        assertThat(whatTheApplicationHolds())
                .as("A SHAPE BECOMES A TEMPLATE AND NEVER A RUN — counted rather than trusted")
                .isEqualTo(before);
    }

    @Test
    void writingTheSameShapeDownTwiceIsRefusedAndTheFirstProcessStands() throws Exception {
        buildTheCompany();
        Proposal proposal = aProposalToWriteDown("WRITE_IT_DOWN", "SHAPE", SHAPE);

        ResponseEntity<String> first = writeItDown(browser, proposal.id(), "Campaign, pressed twice");
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String template =
                json.readTree(first.getBody()).get("processTemplateId").asText();

        ResponseEntity<String> second = writeItDown(browser, proposal.id(), "Campaign, pressed twice again");

        assertThat(second.getStatusCode())
                .as("409 rather than 500 — the refusal has to pass through DiscoveryExceptionHandler")
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(second.getBody()).get("code").asText()).isEqualTo("RECOMMENDATION_ALREADY_DECIDED");

        assertThat(jdbc.queryForObject(
                        "select count(*) from process_template where name like 'Campaign, pressed twice%'", Long.class))
                .as("one process, not two — the claim is taken before the authoring, not after it")
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                        "select produced_template_id from analysis_recommendation where id = ?::uuid",
                        String.class,
                        proposal.id()))
                .as("and the row still points at the first one")
                .isEqualTo(template);
    }

    @Test
    void aProposalThatIsNotAShapeHasNothingToWriteDown() throws Exception {
        buildTheCompany();
        Proposal proposal = aProposalToWriteDown("SPLIT_WORK_TYPE", "WORK_TYPE", "CONTENT");
        Executed before = whatTheApplicationHolds();

        ResponseEntity<String> refused = writeItDown(browser, proposal.id(), "Whatever this would be");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("PROPOSAL_IS_NOT_A_SHAPE");
        assertThat(jdbc.queryForObject(
                        "select count(*) from analysis_recommendation where id = ?::uuid and acted_on_at is not null",
                        Long.class,
                        proposal.id()))
                .as("refused before the claim, so the proposal is still open for whoever should decide it")
                .isEqualTo(0L);
        assertThat(whatTheApplicationHolds()).isEqualTo(before);
    }

    @Test
    void anEmployeeCannotWriteDownWhatTheGraphConcluded() throws Exception {
        buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        Proposal proposal = aProposalToWriteDown("WRITE_IT_DOWN", "SHAPE", SHAPE);
        Executed before = whatTheApplicationHolds();

        ResponseEntity<String> refused = writeItDown(andrei, proposal.id(), "Andrei's process");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject(
                        "select count(*) from process_template where name = 'Andrei''s process'", Long.class))
                .isEqualTo(0L);
        assertThat(whatTheApplicationHolds()).isEqualTo(before);
    }

    @Test
    void aProposalPutAsideLeavesTheQueueAndKeepsItsEvidence() throws Exception {
        buildTheCompany();
        Proposal proposal = aProposalToWriteDown("SPLIT_WORK_TYPE", "WORK_TYPE", "CONTENT");

        ResponseEntity<String> aside =
                browser.post("/api/discovery/analysis/recommendations/" + proposal.id() + "/dismiss", "");

        assertThat(aside.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbc.queryForObject(
                        "select dismissed_by is not null from analysis_recommendation where id = ?::uuid",
                        Boolean.class,
                        proposal.id()))
                .as("who decided, so they can be asked why — attribution, never a figure about them")
                .isTrue();
        assertThat(jdbc.queryForObject(
                        "select headline from analysis_recommendation where id = ?::uuid", String.class, proposal.id()))
                .as("not hidden and not deleted: the evidence and the wording survive the decision")
                .isEqualTo("Write down CONTENT as a process");

        ResponseEntity<String> queue = browser.get("/api/discovery/analysis/recommendations");
        assertThat(queue.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(queue.getBody()))
                .as("a list that kept decided proposals would be a log; what somebody opens this for is a queue")
                .isEmpty();

        ResponseEntity<String> again =
                browser.post("/api/discovery/analysis/recommendations/" + proposal.id() + "/dismiss", "");
        assertThat(again.getStatusCode())
                .as("saying nothing would let somebody believe they had undone something")
                .isEqualTo(HttpStatus.CONFLICT);
    }
}
