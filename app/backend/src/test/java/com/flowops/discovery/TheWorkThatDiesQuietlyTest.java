package com.flowops.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.CompanyScenarioTest;
import com.flowops.support.RoundTripClient;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("DISCOVERY-NUDGE-COMPLETION-01")
@Tag("DISCOVERY-MARK-MESSAGE-01")
@Tag("DISCOVERY-ASSIGN-ORPHAN-01")
class TheWorkThatDiesQuietlyTest extends CompanyScenarioTest {
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

    private ResponseEntity<String> mark(
            RoundTripClient who, String messageId, String jobId, String direction, UUID performer) {
        String body = performer == null
                ? "{\"messageId\":\"%s\",\"jobId\":\"%s\",\"direction\":\"%s\",\"performerId\":null}"
                        .formatted(messageId, jobId, direction)
                : "{\"messageId\":\"%s\",\"jobId\":\"%s\",\"direction\":\"%s\",\"performerId\":\"%s\"}"
                        .formatted(messageId, jobId, direction, performer);
        return who.post("/api/discovery/nodes", body);
    }

    private ResponseEntity<String> block(RoundTripClient who, String node, String waitingOn) {
        return who.post("/api/discovery/nodes/" + node + "/block", "{\"waitingOn\":\"%s\"}".formatted(waitingOn));
    }

    private ResponseEntity<String> answerNudge(RoundTripClient who, String node, String answer) {
        return who.post("/api/discovery/nodes/" + node + "/nudge-answer", "{\"answer\":\"%s\"}".formatted(answer));
    }

    private void deactivate(UUID person) {
        UUID membership =
                jdbc.queryForObject("select id from workspace_membership where user_id = ?", UUID.class, person);
        ResponseEntity<String> gone = browser.post("/api/workspace/people/" + membership + "/deactivate", "");
        assertThat(gone.getStatusCode())
                .as("%s must actually have left, or the case this file is about never arises", person)
                .isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject(
                        "select status from workspace_membership where user_id = ?", String.class, person))
                .isNotEqualTo("ACTIVE");
    }

    private String stateOf(String node) {
        return jdbc.queryForObject("select state from work_node where id = ?::uuid", String.class, node);
    }

    private String trackOf(String node) {
        return jdbc.queryForObject("select track_id from work_node where id = ?::uuid", String.class, node);
    }

    private long phaseRowsOn(String node) {
        return jdbc.queryForObject(
                "select count(*) from node_phase_row where work_node_id = ?::uuid", Long.class, node);
    }

    private JsonNode orphanQueue(RoundTripClient who) throws Exception {
        ResponseEntity<String> waiting = who.get("/api/discovery/orphans");
        assertThat(waiting.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(waiting.getBody());
    }

    @Test
    void soloWorkNobodyEverStartedLapsesWhenSheDropsIt() throws Exception {
        Company company = buildTheCompany();
        String conversation = conversationBetween(browser, company.andrei());
        String brief = said(browser, conversation, "Aurora Coffee vrea un rebranding complet");
        String solo = openJob(browser, brief, "Rebranding Aurora Coffee")
                .get("nodeId")
                .asText();
        assertThat(stateOf(solo))
                .as("she kept the first piece of work for herself, which is what puts it in Self")
                .isEqualTo("SELF");

        ResponseEntity<String> asked = browser.get("/api/discovery/nudge");
        assertThat(asked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(asked.getBody()).get("nodeId").asText())
                .as("it is her own oldest unanswered work she is asked about")
                .isEqualTo(solo);

        ResponseEntity<String> dropped = answerNudge(browser, solo, "DROPPED");

        assertThat(dropped.getStatusCode())
                .as("Dropped is one of four answers offered on every nudged node, in every state")
                .isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(dropped.getBody()).get("state").asText()).isEqualTo("LAPSED");
        assertThat(stateOf(solo)).isEqualTo("LAPSED");
        assertThat(jdbc.queryForObject("select closed_at from work_node where id = ?::uuid", String.class, solo))
                .as("Lapsed is terminal and is not a kind of closed")
                .isNull();
        assertThat(jdbc.queryForObject("select output_type from work_node where id = ?::uuid", String.class, solo))
                .as("a lapsed node produced nothing and nothing is inferred for it")
                .isNull();
        assertThat(browser.get("/api/discovery/nudge").getStatusCode())
                .as("the question was answered, so it is not asked again")
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void workBlockedOnAClientLapsesWithoutAnybodyPretendingTheWaitEnded() throws Exception {
        Company company = buildTheCompany();
        String conversation = conversationBetween(browser, company.andrei());
        String brief = said(browser, conversation, "Aurora Coffee vrea un rebranding complet");
        String job =
                openJob(browser, brief, "Rebranding Aurora Coffee").get("jobId").asText();
        String asked = said(browser, conversation, "Andrei, imi scrii articolul de blog pentru Aurora?");
        String requested = json.readTree(
                        mark(browser, asked, job, "REQUEST", company.andrei()).getBody())
                .get("nodeId")
                .asText();

        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        assertThat(block(andrei, requested, "CLIENT").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stateOf(requested)).isEqualTo("BLOCKED");
        assertThat(phaseRowsOn(requested))
                .as("one work stretch, sealed, and the client's silence — separate rows from the moment they exist")
                .isEqualTo(2L);

        ResponseEntity<String> dropped = answerNudge(andrei, requested, "DROPPED");

        assertThat(dropped.getStatusCode())
                .as("requiring a Resume first would make him say the block ended when it did not")
                .isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(dropped.getBody()).get("state").asText()).isEqualTo("LAPSED");
        assertThat(stateOf(requested)).isEqualTo("LAPSED");

        assertThat(phaseRowsOn(requested))
                .as("lapsing preserves the rows already recorded; it does not erase them")
                .isEqualTo(2L);
        assertThat(jdbc.queryForObject(
                        "select count(*) from node_phase_row where work_node_id = ?::uuid"
                                + " and phase = 'WORK' and ended_at is not null",
                        Long.class,
                        requested))
                .as("the stretch he actually worked is still there, still sealed")
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                        "select waiting_on from node_phase_row where work_node_id = ?::uuid"
                                + " and phase = 'EXTERNAL_WAIT'",
                        String.class,
                        requested))
                .as("and whose silence it was, which is what makes invariant I4 computable at all")
                .isEqualTo("CLIENT");
    }

    @Test
    void soloWorkRecognisedAsAQuestionLeavesTheThreadItWasThreadedIn() throws Exception {
        Company company = buildTheCompany();
        String conversation = conversationBetween(browser, company.andrei());
        String wondering = said(browser, conversation, "Chiar ne trebuie si varianta tiparita pentru Aurora?");
        String solo = openJob(browser, wondering, "Rebranding Aurora Coffee")
                .get("nodeId")
                .asText();
        assertThat(stateOf(solo)).isEqualTo("SELF");
        assertThat(trackOf(solo))
                .as("it was threaded when it was marked, which is what makes the removal observable")
                .isNotNull();

        ResponseEntity<String> recognised = answerNudge(browser, solo, "WAS_A_QUESTION");

        assertThat(recognised.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(recognised.getBody()).get("state").asText()).isEqualTo("QUERY");
        assertThat(stateOf(solo)).isEqualTo("QUERY");
        assertThat(jdbc.queryForObject("select direction from work_node where id = ?::uuid", String.class, solo))
                .as("a person said so; the words were never read")
                .isEqualTo("QUERY");
        assertThat(trackOf(solo))
                .as("a question occupies no position in a thread — invariant I11")
                .isNull();
    }

    @Test
    void aRequestNamingSomebodyWhoHasLeftIsKeptAsAnOrphanAndReachesTheWeeklyQueue() throws Exception {
        Company company = buildTheCompany();
        String withElena = conversationBetween(browser, company.elena());
        String brief = said(browser, withElena, "Aurora Coffee vrea un rebranding complet");
        String job =
                openJob(browser, brief, "Rebranding Aurora Coffee").get("jobId").asText();
        String asked = said(browser, withElena, "Elena, imi faci textele de social pentru Aurora?");

        deactivate(company.elena());

        ResponseEntity<String> marked = mark(browser, asked, job, "REQUEST", company.elena());

        assertThat(marked.getStatusCode())
                .as("the sentence is still evidence of work that happened, so the click is not refused")
                .isEqualTo(HttpStatus.CREATED);
        JsonNode body = json.readTree(marked.getBody());
        String stranded = body.get("nodeId").asText();
        assertThat(body.hasNonNull("trackId"))
                .as("no signal could place it, and a null thread is a real answer rather than a failure")
                .isFalse();
        assertThat(trackOf(stranded)).isNull();

        JsonNode queue = orphanQueue(browser);
        assertThat(queue.size()).as("the queue finally has a supply").isEqualTo(1);
        assertThat(queue.get(0).get("nodeId").asText()).isEqualTo(stranded);
        assertThat(queue.get(0).get("text").asText())
                .as("named by the words it was marked from, or a manager cannot place it")
                .isEqualTo("Elena, imi faci textele de social pentru Aurora?");
        assertThat(queue.get(0).get("jobId").asText()).isEqualTo(job);
    }

    @Test
    void aRequestNamingNobodyIsThreadedOnItsCreatorAndNeverReachesTheQueue() throws Exception {
        Company company = buildTheCompany();
        String conversation = conversationBetween(browser, company.andrei());
        String brief = said(browser, conversation, "Aurora Coffee vrea un rebranding complet");
        String job =
                openJob(browser, brief, "Rebranding Aurora Coffee").get("jobId").asText();
        String unassigned = said(browser, conversation, "Trebuie sa pregatim si prezentarea pentru Aurora");

        ResponseEntity<String> marked = mark(browser, unassigned, job, "REQUEST", null);

        assertThat(marked.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = json.readTree(marked.getBody());
        String node = body.get("nodeId").asText();
        assertThat(body.hasNonNull("trackId"))
                .as("nobody is missing from this sentence, so there is nothing false to build")
                .isTrue();
        assertThat(trackOf(node)).isNotNull();
        assertThat(jdbc.queryForObject(
                        "select performer_id from track where id = ?::uuid", String.class, trackOf(node)))
                .as("the creator is the honest answer here, not a degraded one")
                .isEqualTo(company.maria().toString());

        assertThat(orphanQueue(browser).size())
                .as("an empty queue is right when nothing failed to key — and it is no longer empty by construction")
                .isZero();
    }
}
