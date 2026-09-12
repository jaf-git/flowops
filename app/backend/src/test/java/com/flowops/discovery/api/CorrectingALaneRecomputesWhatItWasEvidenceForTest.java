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

@Tag("DISCOVERY-CORRECT-TRACK-01")
class CorrectingALaneRecomputesWhatItWasEvidenceForTest extends CompanyScenarioTest {
    private record Thread(String trackId, String requestNodeId, String completionNodeId) {}

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

    private String openJob(String messageId, String name) throws Exception {
        ResponseEntity<String> opened = browser.post(
                "/api/discovery/jobs", "{\"messageId\":\"%s\",\"name\":\"%s\"}".formatted(messageId, name));
        assertThat(opened.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(opened.getBody()).get("jobId").asText();
    }

    private JsonNode mark(String messageId, String jobId, String direction, UUID performer) throws Exception {
        String body = performer == null
                ? "{\"messageId\":\"%s\",\"jobId\":\"%s\",\"direction\":\"%s\",\"performerId\":null}"
                        .formatted(messageId, jobId, direction)
                : "{\"messageId\":\"%s\",\"jobId\":\"%s\",\"direction\":\"%s\",\"performerId\":\"%s\"}"
                        .formatted(messageId, jobId, direction, performer);
        ResponseEntity<String> marked = browser.post("/api/discovery/nodes", body);
        assertThat(marked.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(marked.getBody());
    }

    private ResponseEntity<String> moveToLane(RoundTripClient who, String nodeId, String trackId) {
        return who.patch("/api/discovery/nodes/" + nodeId + "/lane", "{\"trackId\":\"%s\"}".formatted(trackId));
    }

    private Thread aPieceOfWorkAskedForAndDelivered(
            RoundTripClient andrei, UUID andreiId, String job, String conversation) throws Exception {
        String asked = said(browser, conversation, "Andrei, poti sa faci asta pana joi?");
        JsonNode request = mark(asked, job, "REQUEST", andreiId);

        String finished = said(andrei, conversation, "Gata, e in drive");
        JsonNode completion = mark(finished, job, "COMPLETION", null);
        assertThat(andrei.post(
                                "/api/discovery/nodes/"
                                        + completion.get("nodeId").asText() + "/output",
                                "{\"outputType\":\"TEXT\"}")
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        String trackId = request.get("trackId").asText();
        ResponseEntity<String> ended = browser.post("/api/discovery/tracks/" + trackId + "/end", null);
        assertThat(ended.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(ended.getBody()).get("completeness").asText())
                .as("only a thread that qualifies is fingerprinted, and only a fingerprinted thread can cluster")
                .isEqualTo("COMPLETE");

        return new Thread(
                trackId,
                request.get("nodeId").asText(),
                completion.get("nodeId").asText());
    }

    private String anOpenLaneWith(UUID performer, String sentence, String job, String conversation) throws Exception {
        String asked = said(browser, conversation, sentence);
        return mark(asked, job, "REQUEST", performer).get("trackId").asText();
    }

    private String elenasLane(UUID elena, String job, String conversation) throws Exception {
        return anOpenLaneWith(elena, "Elena, ne trebuie si un afis pentru vitrina", job, conversation);
    }

    private String laneOf(String nodeId) {
        return jdbc.queryForObject("select track_id::text from work_node where id = ?::uuid", String.class, nodeId);
    }

    private Integer evidenceBehindTheOnlyType() {
        return jdbc.queryForObject("select occurrence_count from track_type", Integer.class);
    }

    private String statusOfTheOnlyType() {
        return jdbc.queryForObject("select status from track_type", String.class);
    }

    private String codeOf(ResponseEntity<String> refused) throws Exception {
        return json.readTree(refused.getBody()).get("code").asText();
    }

    @Test
    void aCardMovesToAnotherLaneInTheSameEngagement() throws Exception {
        Company company = buildTheCompany();
        String conversation = conversationBetween(browser, company.andrei());
        String brief = said(browser, conversation, "Aurora Coffee vrea materiale pentru vara");
        String job = openJob(brief, "Aurora Coffee — materiale de vara");

        String asked = said(browser, conversation, "Andrei, poti sa faci asta pana joi?");
        JsonNode misfiled = mark(asked, job, "REQUEST", company.andrei());
        String elenaLane = elenasLane(company.elena(), job, conversation);

        String card = misfiled.get("nodeId").asText();
        assertThat(laneOf(card)).isEqualTo(misfiled.get("trackId").asText());

        assertThat(moveToLane(browser, card, elenaLane).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(laneOf(card))
                .as("the card is in the lane it was dropped into, which is the whole observable effect")
                .isEqualTo(elenaLane);
    }

    @Test
    void takingEvidenceFromANamedTypeLowersItsCountAndDemotesIt() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String conversation = conversationBetween(browser, company.andrei());
        String brief = said(browser, conversation, "Aurora Coffee vrea materiale pentru vara");
        String job = openJob(brief, "Aurora Coffee — materiale de vara");

        Thread first = aPieceOfWorkAskedForAndDelivered(andrei, company.andrei(), job, conversation);
        for (int afternoon = 2; afternoon <= 5; afternoon++) {
            aPieceOfWorkAskedForAndDelivered(andrei, company.andrei(), job, conversation);
        }
        String elenaLane = elenasLane(company.elena(), job, conversation);

        ResponseEntity<String> queue = browser.get("/api/discovery/types");
        assertThat(queue.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode proposal = json.readTree(queue.getBody());
        assertThat(proposal).hasSize(1);
        String typeId = proposal.get(0).get("typeId").asText();
        assertThat(browser.post("/api/discovery/types/" + typeId + "/name", "{\"name\":\"Content production\"}")
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(evidenceBehindTheOnlyType())
                .as("five completed threads is what earned the question, and what the name now rests on")
                .isEqualTo(5);
        assertThat(statusOfTheOnlyType()).isEqualTo("NAMED");

        assertThat(moveToLane(browser, first.requestNodeId(), elenaLane).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(evidenceBehindTheOnlyType())
                .as("the thread the card left is a different shape now, so it is no longer one of the five")
                .isEqualTo(4);
        assertThat(statusOfTheOnlyType())
                .as("four does not clear the floor of five, and a type never stays NAMED below its evidence — I13")
                .isEqualTo("PROVISIONAL");
        assertThat(jdbc.queryForObject("select name from track_type", String.class))
                .as("the name survives the demotion, so the evidence returning does not ask her to name it twice")
                .isEqualTo("Content production");
        assertThat(jdbc.queryForObject("select confirmed_by_owner from track_type", Boolean.class))
                .as("and so does her confirmation — only the status moved")
                .isTrue();
    }

    @Test
    void aMoveIntoAnotherEngagementsThreadIsRefused() throws Exception {
        Company company = buildTheCompany();
        String conversation = conversationBetween(browser, company.andrei());

        String auroraBrief = said(browser, conversation, "Aurora Coffee vrea materiale pentru vara");
        String aurora = openJob(auroraBrief, "Aurora Coffee — materiale de vara");
        String asked = said(browser, conversation, "Andrei, poti sa faci asta pana joi?");
        JsonNode card = mark(asked, aurora, "REQUEST", company.andrei());

        String bistroBrief = said(browser, conversation, "Bistro Verde pregateste meniul de toamna");
        String bistro = openJob(bistroBrief, "Bistro Verde — meniul de toamna");
        String bistrosLane = elenasLane(company.elena(), bistro, conversation);

        String cardId = card.get("nodeId").asText();
        ResponseEntity<String> refused = moveToLane(browser, cardId, bistrosLane);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(codeOf(refused))
                .as("refused as unknown, so nothing is learned about the engagement the caller was not looking at")
                .isEqualTo("UNKNOWN_TRACK");
        assertThat(laneOf(cardId))
                .as("and the card did not move — a refusal that half-applied would be worse than either answer")
                .isEqualTo(card.get("trackId").asText());
    }

    @Test
    void aMoveIntoAClosedThreadIsRefused() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String conversation = conversationBetween(browser, company.andrei());
        String brief = said(browser, conversation, "Aurora Coffee vrea materiale pentru vara");
        String job = openJob(brief, "Aurora Coffee — materiale de vara");

        Thread closed = aPieceOfWorkAskedForAndDelivered(andrei, company.andrei(), job, conversation);
        String openCard = mark(
                        said(browser, conversation, "Elena, ne trebuie si un afis pentru vitrina"),
                        job,
                        "REQUEST",
                        company.elena())
                .get("nodeId")
                .asText();

        ResponseEntity<String> refused = moveToLane(browser, openCard, closed.trackId());

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codeOf(refused)).isEqualTo("TRACK_ALREADY_CLOSED");
    }

    @Test
    void anUnknownCardAndAnUnknownLaneAreBothRefused() throws Exception {
        Company company = buildTheCompany();
        String conversation = conversationBetween(browser, company.andrei());
        String brief = said(browser, conversation, "Aurora Coffee vrea materiale pentru vara");
        String job = openJob(brief, "Aurora Coffee — materiale de vara");
        JsonNode card = mark(
                said(browser, conversation, "Andrei, poti sa faci asta pana joi?"), job, "REQUEST", company.andrei());

        ResponseEntity<String> noCard = moveToLane(
                browser, UUID.randomUUID().toString(), card.get("trackId").asText());
        assertThat(noCard.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(codeOf(noCard)).isEqualTo("UNKNOWN_WORK_NODE");

        ResponseEntity<String> noLane = moveToLane(
                browser, card.get("nodeId").asText(), UUID.randomUUID().toString());
        assertThat(noLane.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(codeOf(noLane)).isEqualTo("UNKNOWN_TRACK");
    }

    @Test
    void aManagerMayEmptyTheOrphanQueueAndMayNotMoveAPlacedCard() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient ionut = signedInBrowser("ionut@atelier.ro");
        String conversation = conversationBetween(browser, company.andrei());
        String brief = said(browser, conversation, "Aurora Coffee vrea materiale pentru vara");
        String job = openJob(brief, "Aurora Coffee — materiale de vara");

        JsonNode placed = mark(
                said(browser, conversation, "Andrei, poti sa faci asta pana joi?"), job, "REQUEST", company.andrei());

        assertThat(jdbc.update("update workspace_membership set status = 'REMOVED' where user_id = ?", company.elena()))
                .isEqualTo(1);
        JsonNode stranded = mark(
                said(browser, conversation, "Elena, ne trebuie si un afis pentru vitrina"),
                job,
                "REQUEST",
                company.elena());
        assertThat(stranded.get("trackId").isNull())
                .as("a request naming somebody who is gone has no thread, which is a real state and not a failure")
                .isTrue();

        ResponseEntity<String> queue = ionut.get("/api/discovery/orphans");
        assertThat(queue.getStatusCode())
                .as("the weekly queue is the manager's — WORK_NODE_ASSIGN_SUBJECT, which he holds")
                .isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(queue.getBody())).hasSize(1);

        String strandedId = stranded.get("nodeId").asText();
        String andreisLane = placed.get("trackId").asText();
        assertThat(ionut.patch(
                                "/api/discovery/nodes/" + strandedId + "/track",
                                "{\"trackId\":\"%s\"}".formatted(andreisLane))
                        .getStatusCode())
                .as("he places the stranded work, which is the classification the signals could not make")
                .isEqualTo(HttpStatus.OK);
        assertThat(laneOf(strandedId)).isEqualTo(andreisLane);

        String ioanasLane = anOpenLaneWith(company.ioana(), "Ioana, poti sa te uiti peste text?", job, conversation);
        ResponseEntity<String> refused = moveToLane(ionut, placed.get("nodeId").asText(), ioanasLane);

        assertThat(refused.getStatusCode())
                .as("the same manager, one endpoint away, moving a card that is already placed")
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(codeOf(refused)).isEqualTo("NOT_PERMITTED");
        assertThat(laneOf(placed.get("nodeId").asText()))
                .as("and the card stayed where it was")
                .isEqualTo(andreisLane);
    }
}
