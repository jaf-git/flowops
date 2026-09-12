package com.flowops.discovery.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.CompanyScenarioTest;
import com.flowops.support.RoundTripClient;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("DISCOVERY-MARK-MESSAGE-01")
@Tag("DISCOVERY-SET-SUBJECT-01")
class TheTenSecondUndoTest extends CompanyScenarioTest {
    private String conversationBetween(RoundTripClient who, UUID person) throws Exception {
        ResponseEntity<String> started = who.post("/api/conversations", "{\"personId\":\"%s\"}".formatted(person));
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(started.getBody()).get("id").asText();
    }

    private String said(RoundTripClient who, String conversation, String body) throws Exception {
        ResponseEntity<String> sent =
                who.post("/api/conversations/" + conversation + "/messages", "{\"body\":\"%s\"}".formatted(body));
        assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(sent.getBody()).get("id").asText();
    }

    private JsonNode openJob(RoundTripClient who, String messageId, String name) throws Exception {
        ResponseEntity<String> opened =
                who.post("/api/discovery/jobs", "{\"messageId\":\"%s\",\"name\":\"%s\"}".formatted(messageId, name));
        assertThat(opened.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(opened.getBody());
    }

    private JsonNode mark(RoundTripClient who, String messageId, String jobId, String direction, UUID performer)
            throws Exception {
        String body = performer == null
                ? "{\"messageId\":\"%s\",\"jobId\":\"%s\",\"direction\":\"%s\",\"performerId\":null}"
                        .formatted(messageId, jobId, direction)
                : "{\"messageId\":\"%s\",\"jobId\":\"%s\",\"direction\":\"%s\",\"performerId\":\"%s\"}"
                        .formatted(messageId, jobId, direction, performer);
        ResponseEntity<String> marked = who.post("/api/discovery/nodes", body);
        assertThat(marked.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(marked.getBody());
    }

    private void markedAnHourAgo(String node) {
        assertThat(jdbc.update(
                        "update work_node set created_at = created_at - interval '1 hour' where id = ?::uuid", node))
                .as("the node must actually have been aged, or the window is not being tested")
                .isEqualTo(1);
    }

    private long rowsFor(String table, String column, String id) {
        return jdbc.queryForObject(
                "select count(*) from %s where %s = ?::uuid".formatted(table, column), Long.class, id);
    }

    @Test
    void anUndoInsideTheWindowDeletesTheUnitOfWorkAndItsEvidence() throws Exception {
        Company company = buildTheCompany();
        String conversation = conversationBetween(browser, company.andrei());
        String brief = said(browser, conversation, "Aurora Coffee vrea un rebranding complet");
        String job =
                openJob(browser, brief, "Rebranding Aurora Coffee").get("jobId").asText();

        String first = said(browser, conversation, "Andrei, poti sa faci moodboard-ul pana joi?");
        String second = said(browser, conversation, "Si variantele de logo, tot pentru joi");
        JsonNode moodboard = mark(browser, first, job, "REQUEST", company.andrei());
        JsonNode logos = mark(browser, second, job, "REQUEST", company.andrei());
        String thread = logos.get("trackId").asText();
        assertThat(moodboard.get("trackId").asText())
                .as("two pieces of work by the same pair belong together, which is what makes the thread survive")
                .isEqualTo(thread);

        ResponseEntity<String> undone =
                browser.delete("/api/discovery/nodes/" + logos.get("nodeId").asText());

        assertThat(undone.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(rowsFor("work_node", "id", logos.get("nodeId").asText()))
                .as("a full delete, not a tombstone — a tombstone is a row every later query has to filter")
                .isZero();
        assertThat(rowsFor(
                        "work_node_evidence",
                        "work_node_id",
                        logos.get("nodeId").asText()))
                .as("the evidence goes with the node, or the message stays marked on every screen that reads it")
                .isZero();
        assertThat(rowsFor("work_node", "id", moodboard.get("nodeId").asText()))
                .as("the work she meant to mark is untouched")
                .isEqualTo(1L);
        assertThat(rowsFor("track", "id", thread))
                .as("the thread still holds the moodboard, so it stays")
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from message where id = ?::uuid", Long.class, second))
                .as("the message itself is untouched — the node is editable, the message is evidence")
                .isEqualTo(1L);
    }

    @Test
    void undoingTheOnlyUnitOfWorkInAThreadTakesTheThreadWithIt() throws Exception {
        Company company = buildTheCompany();
        String conversation = conversationBetween(browser, company.andrei());
        String brief = said(browser, conversation, "Aurora Coffee vrea un rebranding complet");
        JsonNode opened = openJob(browser, brief, "Rebranding Aurora Coffee");
        String job = opened.get("jobId").asText();
        String jobStartThread = opened.get("trackId").asText();

        String asked = said(browser, conversation, "Andrei, poti sa faci moodboard-ul pana joi?");
        JsonNode moodboard = mark(browser, asked, job, "REQUEST", company.andrei());
        String thread = moodboard.get("trackId").asText();
        assertThat(thread).isNotEqualTo(jobStartThread);

        ResponseEntity<String> undone =
                browser.delete("/api/discovery/nodes/" + moodboard.get("nodeId").asText());

        assertThat(undone.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(rowsFor("track", "id", thread))
                .as("a thread with nothing in it describes nothing")
                .isZero();
        assertThat(rowsFor("track", "id", jobStartThread))
                .as("and the thread the engagement opened with is not swept up with it")
                .isEqualTo(1L);
        assertThat(rowsFor("job", "id", job))
                .as("the engagement stays — this was not the click that opened it")
                .isEqualTo(1L);
    }

    @Test
    void undoingTheClickThatOpenedAnEngagementTakesTheEngagementWithIt() throws Exception {
        Company company = buildTheCompany();
        String conversation = conversationBetween(browser, company.andrei());
        String brief = said(browser, conversation, "Aurora Coffee vrea un rebranding complet");
        JsonNode opened = openJob(browser, brief, "Rebranding gresit");
        String job = opened.get("jobId").asText();

        ResponseEntity<String> undone =
                browser.delete("/api/discovery/nodes/" + opened.get("nodeId").asText());

        assertThat(undone.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(rowsFor("work_node", "id", opened.get("nodeId").asText())).isZero();
        assertThat(rowsFor("track", "id", opened.get("trackId").asText())).isZero();
        assertThat(rowsFor("job", "id", job))
                .as("an engagement whose opening click was withdrawn is one nobody opened")
                .isZero();
    }

    @Test
    void anUndoPastTheWindowIsRefusedAndTheWorkStays() throws Exception {
        Company company = buildTheCompany();
        String conversation = conversationBetween(browser, company.andrei());
        String brief = said(browser, conversation, "Aurora Coffee vrea un rebranding complet");
        String job =
                openJob(browser, brief, "Rebranding Aurora Coffee").get("jobId").asText();

        String asked = said(browser, conversation, "Andrei, poti sa faci moodboard-ul pana joi?");
        JsonNode moodboard = mark(browser, asked, job, "REQUEST", company.andrei());
        String node = moodboard.get("nodeId").asText();
        markedAnHourAgo(node);

        ResponseEntity<String> refused = browser.delete("/api/discovery/nodes/" + node);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("UNDO_WINDOW_CLOSED");
        assertThat(rowsFor("work_node", "id", node))
                .as("the unit of work is still there, and so is everything downstream of it")
                .isEqualTo(1L);
        assertThat(rowsFor("work_node_evidence", "work_node_id", node)).isEqualTo(1L);
        assertThat(rowsFor("track", "id", moodboard.get("trackId").asText())).isEqualTo(1L);
    }

    @Test
    void anUnknownUnitOfWorkIsRefusedRatherThanReportedTakenBack() throws Exception {
        buildTheCompany();

        ResponseEntity<String> refused = browser.delete("/api/discovery/nodes/" + UUID.randomUUID());

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        JsonNode error = json.readTree(refused.getBody());
        assertThat(error.get("code").asText()).isEqualTo("UNKNOWN_WORK_NODE");
        assertThat(error.get("details").get(0).get("field").asText()).isEqualTo("nodeId");
    }

    @Test
    void correctingTheEngagementMovesTheWorkOutOfTheThreadItWasInferredInto() throws Exception {
        Company company = buildTheCompany();
        String conversation = conversationBetween(browser, company.andrei());
        String aurora = said(browser, conversation, "Aurora Coffee vrea un rebranding complet");
        String bistro = said(browser, conversation, "Bistro Verde vrea un meniu nou");
        String rebranding = openJob(browser, aurora, "Rebranding Aurora Coffee")
                .get("jobId")
                .asText();
        String menu =
                openJob(browser, bistro, "Meniu Bistro Verde").get("jobId").asText();

        String asked = said(browser, conversation, "Andrei, poti sa faci moodboard-ul pana joi?");
        JsonNode moodboard = mark(browser, asked, rebranding, "REQUEST", company.andrei());
        String node = moodboard.get("nodeId").asText();
        String inferredThread = moodboard.get("trackId").asText();

        ResponseEntity<String> corrected =
                browser.patch("/api/discovery/nodes/" + node + "/job", "{\"jobId\":\"%s\"}".formatted(menu));

        assertThat(corrected.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = json.readTree(corrected.getBody());
        assertThat(body.get("jobId").asText()).isEqualTo(menu);
        assertThat(body.hasNonNull("trackId"))
                .as("the work is threaded again inside the engagement it actually belongs to")
                .isTrue();
        assertThat(body.get("trackId").asText())
                .as("a thread never spans two engagements")
                .isNotEqualTo(inferredThread);

        assertThat(jdbc.queryForObject("select job_id from work_node where id = ?::uuid", String.class, node))
                .as("the engagement moved in the row and not only in the response — red here means"
                        + " WorkGraphPersistenceAdapter.SAVE_NODE has no `job_id = excluded.job_id` in its"
                        + " conflict clause, which leaves the node pointing at its old engagement while its"
                        + " thread belongs to the new one")
                .isEqualTo(menu);
        assertThat(jdbc.queryForObject("select track_id from work_node where id = ?::uuid", String.class, node))
                .as("the row agrees with the response, which is what the clusterer will read")
                .isEqualTo(body.get("trackId").asText());
        assertThat(jdbc.queryForObject("select subject_source from work_node where id = ?::uuid", String.class, node))
                .as("a guess and a person's answer are not the same evidence")
                .isEqualTo("CORRECTED");
        assertThat(jdbc.queryForObject(
                        "select job_id from track where id = ?::uuid",
                        String.class,
                        body.get("trackId").asText()))
                .isEqualTo(menu);
        assertThat(rowsFor("track", "id", inferredThread))
                .as("the thread she took the work out of had nothing else in it")
                .isZero();
    }

    @Test
    void acceptingTheGuessRecordsThatSomebodySawItAndChangesNothingElse() throws Exception {
        Company company = buildTheCompany();
        String conversation = conversationBetween(browser, company.andrei());
        String brief = said(browser, conversation, "Aurora Coffee vrea un rebranding complet");
        String job =
                openJob(browser, brief, "Rebranding Aurora Coffee").get("jobId").asText();

        String asked = said(browser, conversation, "Andrei, poti sa faci moodboard-ul pana joi?");
        JsonNode moodboard = mark(browser, asked, job, "REQUEST", company.andrei());
        String node = moodboard.get("nodeId").asText();

        ResponseEntity<String> confirmed =
                browser.patch("/api/discovery/nodes/" + node + "/job", "{\"jobId\":\"%s\"}".formatted(job));

        assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = json.readTree(confirmed.getBody());
        assertThat(body.get("jobId").asText()).isEqualTo(job);
        assertThat(body.get("trackId").asText())
                .as("accepting the guess moves nothing")
                .isEqualTo(moodboard.get("trackId").asText());
        assertThat(jdbc.queryForObject("select subject_source from work_node where id = ?::uuid", String.class, node))
                .isEqualTo("CONFIRMED");
    }

    @Test
    void correctingOntoAnEngagementThatDoesNotExistIsRefused() throws Exception {
        Company company = buildTheCompany();
        String conversation = conversationBetween(browser, company.andrei());
        String brief = said(browser, conversation, "Aurora Coffee vrea un rebranding complet");
        String job =
                openJob(browser, brief, "Rebranding Aurora Coffee").get("jobId").asText();
        String asked = said(browser, conversation, "Andrei, poti sa faci moodboard-ul pana joi?");
        String node = mark(browser, asked, job, "REQUEST", company.andrei())
                .get("nodeId")
                .asText();

        ResponseEntity<String> refused = browser.patch(
                "/api/discovery/nodes/" + node + "/job", "{\"jobId\":\"%s\"}".formatted(UUID.randomUUID()));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("UNKNOWN_JOB");
        assertThat(jdbc.queryForObject("select job_id from work_node where id = ?::uuid", String.class, node))
                .as("nothing moved, and no engagement was conjured to move it to")
                .isEqualTo(job);
        assertThat(jdbc.queryForObject("select count(*) from job", Long.class)).isEqualTo(1L);
    }

    @Test
    void theChipGuessesTheEngagementThisConversationIsAlreadyWorkingOn() throws Exception {
        Company company = buildTheCompany();
        String withAndrei = conversationBetween(browser, company.andrei());
        String withElena = conversationBetween(browser, company.elena());
        String aurora = said(browser, withAndrei, "Aurora Coffee vrea un rebranding complet");
        String bistro = said(browser, withElena, "Bistro Verde vrea un meniu nou");
        String rebranding = openJob(browser, aurora, "Rebranding Aurora Coffee")
                .get("jobId")
                .asText();
        String menu =
                openJob(browser, bistro, "Meniu Bistro Verde").get("jobId").asText();

        ResponseEntity<String> offered = browser.get("/api/discovery/jobs?conversationId=" + withAndrei);

        assertThat(offered.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode offers = json.readTree(offered.getBody());
        assertThat(offers.isArray()).isTrue();

        JsonNode guessed = null;
        boolean bistroOffered = false;
        int flagged = 0;
        for (JsonNode offer : offers) {
            if (offer.get("guessed").asBoolean()) {
                flagged++;
                guessed = offer;
            }
            bistroOffered |= menu.equals(offer.get("jobId").asText());
        }

        assertThat(flagged)
                .as("at most one entry is guessed, and the chip pre-selects exactly that one")
                .isEqualTo(1);
        assertThat(guessed.get("jobId").asText())
                .as("the engagement this conversation has already produced work in")
                .isEqualTo(rebranding);
        assertThat(guessed.get("name").asText()).isEqualTo("Rebranding Aurora Coffee");
        assertThat(bistroOffered)
                .as("the alternative is offered unflagged, so correcting the guess stays one tap")
                .isTrue();

        assertThat(jdbc.queryForObject("select count(*) from work_node where subject_source is not null", Long.class))
                .as("the endpoint never pre-applies the guess — it is shown, not written")
                .isZero();
    }

    @Test
    void nothingIsGuessedWhenTheWorkspaceHasNoOpenEngagement() throws Exception {
        Company company = buildTheCompany();
        String conversation = conversationBetween(browser, company.andrei());

        ResponseEntity<String> offered = browser.get("/api/discovery/jobs?conversationId=" + conversation);

        assertThat(offered.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode offers = json.readTree(offered.getBody());
        assertThat(offers.isArray()).isTrue();
        assertThat(offers.size())
                .as("no engagement exists, so there is nothing to offer and nothing to guess")
                .isZero();
    }
}
